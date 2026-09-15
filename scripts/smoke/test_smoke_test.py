"""smoke_test.py 자체 테스트.

운영 서버에 요청을 보내기 전에 스크립트가 틀리지 않았는지 확인하려고 둔다.
스모크 테스트 워크플로가 실제 요청보다 먼저 이 테스트를 돌린다.

로컬 가짜 서버를 띄워 두고, 그 서버가 받은 요청을 전부 기록해서
"GET 만, 고정 경로로만, 정해진 횟수 안에서" 보내는지까지 확인한다.

실행: python3 -m unittest discover -s scripts/smoke -p 'test_*.py' -v
"""

import base64
import hashlib
import hmac
import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import smoke_test  # noqa: E402

SERVER_SECRET = base64.b64encode(b"s" * 64).decode()
OTHER_SECRET = base64.b64encode(b"x" * 64).decode()
NOW = 1_800_000_000


def _b64url_decode(part: str) -> bytes:
    return base64.urlsafe_b64decode(part + "=" * (-len(part) % 4))


def verify_token(token: str, secret_b64: str):
    """서버의 JwtTokenFilter 가 하는 일(서명 검증 후 클레임 읽기)을 흉내 낸다."""
    header, payload, signature = token.split(".")
    key = base64.b64decode(secret_b64)
    expected = hmac.new(key, f"{header}.{payload}".encode(), hashlib.sha256).digest()
    if not hmac.compare_digest(expected, _b64url_decode(signature)):
        return None
    return json.loads(_b64url_decode(payload))


class FakeServer:
    """시나리오 함수가 (status, headers, body) 를 돌려주는 가짜 서버."""

    def __init__(self, scenario):
        self.requests = []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def _handle(self):
                outer.requests.append((self.command, self.path, dict(self.headers)))
                status, headers, body = scenario(self.command, self.path, dict(self.headers))
                headers = dict(headers)
                # send_response 가 Server 헤더를 먼저 붙이므로, 시나리오가 준 값으로 바꿔 끼운다.
                server = headers.pop("Server", None)
                if server:
                    self.version_string = lambda: server
                try:
                    self.send_response(status)
                    for k, v in headers.items():
                        self.send_header(k, v)
                    self.end_headers()
                    self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    # 시간 초과 테스트에서 클라이언트가 먼저 끊은 경우다.
                    pass

            do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = _handle

            def log_message(self, *args):
                pass

        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.httpd.server_address[1]}"
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def close(self):
        self.httpd.shutdown()
        self.httpd.server_close()


def json_body(obj):
    return {"Content-Type": "application/json"}, json.dumps(obj).encode()


def healthy_server(command, path, headers):
    """정상 운영 서버. 토큰이 없으면 1007, 서명이 틀리면 1009, 맞으면 count 를 준다."""
    auth = headers.get("Authorization")
    if not auth:
        h, b = json_body({"errorCode": 1007, "message": "인증이 실패했습니다."})
        return 401, h, b
    claims = verify_token(auth.removeprefix("Bearer "), SERVER_SECRET)
    if claims is None:
        h, b = json_body({"errorCode": 1009, "message": "유효하지 않은 엑세스 토큰입니다."})
        return 401, h, b
    h, b = json_body({"data": 0})
    return 200, h, b


class SmokeTestScriptTest(unittest.TestCase):

    def setUp(self):
        self.sleeps = []
        self.server = None

    def tearDown(self):
        if self.server:
            self.server.close()

    def start(self, scenario):
        self.server = FakeServer(scenario)
        return self.server

    def run_script(self, secret=None, extra_env=None):
        env = {"SMOKE_BASE_URL": self.server.url}
        if secret is not None:
            env["SMOKE_ACCESS_TOKEN_SECRET"] = secret
        env.update(extra_env or {})
        return smoke_test.run(env, sleep=self.sleeps.append, clock=lambda: NOW)

    def assert_only_safe_gets(self):
        self.assertTrue(self.server.requests)
        for method, path, _ in self.server.requests:
            self.assertEqual("GET", method)
            self.assertEqual(smoke_test.SAFE_PATH, path)
        self.assertLessEqual(len(self.server.requests), 2 * smoke_test.MAX_ATTEMPTS)

    # --- 정상 흐름 ---

    def test_passes_against_healthy_server(self):
        self.start(healthy_server)
        self.assertEqual(0, self.run_script(secret=SERVER_SECRET))
        self.assertEqual(2, len(self.server.requests))
        self.assert_only_safe_gets()

    def test_skips_auth_check_without_secret(self):
        self.start(healthy_server)
        self.assertEqual(0, self.run_script())
        self.assertEqual(1, len(self.server.requests))
        self.assertNotIn("Authorization", self.server.requests[0][2])

    def test_token_targets_nonexistent_user_with_short_ttl(self):
        self.start(healthy_server)
        self.run_script(secret=SERVER_SECRET)
        token = self.server.requests[1][2]["Authorization"].removeprefix("Bearer ")
        claims = verify_token(token, SERVER_SECRET)
        self.assertEqual("0", claims["sub"])
        self.assertEqual("ROLE_GUEST", claims["authority"])
        self.assertEqual(NOW, claims["iat"])
        self.assertEqual(smoke_test.TOKEN_TTL_SECONDS, claims["exp"] - claims["iat"])

    def test_writes_step_summary(self):
        self.start(healthy_server)
        with tempfile.NamedTemporaryFile("r", suffix=".md", delete=False) as f:
            path = f.name
        try:
            self.run_script(secret=SERVER_SECRET, extra_env={"GITHUB_STEP_SUMMARY": path})
            with open(path, encoding="utf-8") as f:
                summary = f.read()
            self.assertIn("## 스모크 테스트", summary)
            self.assertEqual(2, summary.count("✅ 통과"))
        finally:
            os.remove(path)

    # --- 실패를 실패로 잡는가 ---

    def test_fails_when_signing_key_differs(self):
        self.start(healthy_server)
        self.assertEqual(1, self.run_script(secret=OTHER_SECRET))
        self.assertEqual([], self.sleeps, "4xx 는 재시도하지 않는다")

    def test_fails_when_endpoint_is_open_without_token(self):
        def open_server(command, path, headers):
            h, b = json_body({"data": 0})
            return 200, h, b
        self.start(open_server)
        self.assertEqual(1, self.run_script(secret=SERVER_SECRET))
        self.assertEqual(1, len(self.server.requests), "첫 확인이 실패하면 토큰 요청을 보내지 않는다")

    def test_retries_bad_gateway_then_fails(self):
        def bad_gateway(command, path, headers):
            return 502, {"Content-Type": "text/html"}, b"<html>502 Bad Gateway</html>"
        self.start(bad_gateway)
        self.assertEqual(1, self.run_script(secret=SERVER_SECRET))
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))
        self.assertEqual([smoke_test.RETRY_DELAY_SECONDS] * (smoke_test.MAX_ATTEMPTS - 1), self.sleeps)
        self.assert_only_safe_gets()

    def test_recovers_when_first_attempt_fails(self):
        calls = {"n": 0}

        def flaky(command, path, headers):
            calls["n"] += 1
            if calls["n"] == 1:
                return 503, {}, b""
            return healthy_server(command, path, headers)
        self.start(flaky)
        self.assertEqual(0, self.run_script(secret=SERVER_SECRET))
        self.assertEqual(3, len(self.server.requests))

    def test_fails_on_html_page(self):
        def maintenance(command, path, headers):
            return 200, {"Content-Type": "text/html"}, b"<html>maintenance</html>"
        self.start(maintenance)
        self.assertEqual(1, self.run_script())

    def test_fails_on_maintenance_json_without_error_code(self):
        def maintenance(command, path, headers):
            h, b = json_body({"errorCode": None, "message": "점검 중입니다"})
            return 400, h, b
        self.start(maintenance)
        self.assertEqual(1, self.run_script())
        self.assertEqual([], self.sleeps)

    def test_reports_cloudflare_block_separately(self):
        def cloudflare(command, path, headers):
            return 403, {"Server": "cloudflare", "cf-mitigated": "challenge"}, b"<html>Just a moment...</html>"
        self.start(cloudflare)
        self.assertEqual(1, self.run_script())
        res = smoke_test.check_reachable(self.server.url, sleep=lambda s: None)
        self.assertIn("Cloudflare", res.detail)

    def test_reports_origin_unreachable_behind_cloudflare(self):
        # 2026-09-15 dev 도메인에서 실제로 받은 응답 모양이다 (server: cloudflare, 본문 "error code: 502").
        def origin_down(command, path, headers):
            return 502, {"Server": "cloudflare", "Content-Type": "text/plain; charset=UTF-8"}, b"error code: 502"
        self.start(origin_down)
        res = smoke_test.check_reachable(self.server.url, sleep=self.sleeps.append)
        self.assertFalse(res.passed)
        self.assertIn("원 서버에 닿지 못했습니다", res.detail)
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))

    def test_fails_when_server_is_unreachable(self):
        self.start(healthy_server)
        url = self.server.url
        self.server.close()
        self.server = None
        env = {"SMOKE_BASE_URL": url}
        self.assertEqual(1, smoke_test.run(env, sleep=self.sleeps.append, clock=lambda: NOW))
        self.assertEqual(smoke_test.MAX_ATTEMPTS - 1, len(self.sleeps))

    def test_does_not_follow_redirect_and_does_not_leak_token(self):
        # 리다이렉트를 따라가면 urllib 이 Authorization 헤더까지 옮긴다. 따라가지 않아야 한다.
        def redirecting(command, path, headers):
            if path == smoke_test.SAFE_PATH and not headers.get("Authorization"):
                return healthy_server(command, path, headers)
            if path == smoke_test.SAFE_PATH:
                return 302, {"Location": "/leak"}, b""
            return 200, {}, b"leaked"
        self.start(redirecting)
        self.assertEqual(1, self.run_script(secret=SERVER_SECRET))
        self.assertEqual([smoke_test.SAFE_PATH, smoke_test.SAFE_PATH], [p for _, p, _ in self.server.requests])
        self.assertEqual([], self.sleeps, "3xx 는 재시도하지 않는다")

    def test_redirect_on_first_check_fails(self):
        def maintenance_redirect(command, path, headers):
            return 301, {"Location": "https://example.com/maintenance"}, b""
        self.start(maintenance_redirect)
        res = smoke_test.check_reachable(self.server.url, sleep=self.sleeps.append)
        self.assertFalse(res.passed)
        self.assertIn("리다이렉트", res.detail)
        self.assertEqual(1, len(self.server.requests))

    def test_old_server_reports_wrong_key_as_expired(self):
        # 운영 main(2026-07-01)은 서명이 틀려도 1005 를 준다. 실패로 잡고 서명 키 가능성도 안내해야 한다.
        def old_server(command, path, headers):
            if not headers.get("Authorization"):
                return healthy_server(command, path, headers)
            h, b = json_body({"errorCode": 1005, "message": "엑세스 토큰이 만료되었습니다."})
            return 401, h, b
        self.start(old_server)
        res = smoke_test.check_authenticated(self.server.url, OTHER_SECRET, NOW, sleep=self.sleeps.append)
        self.assertFalse(res.passed)
        self.assertIn("서명 키", res.detail)

    def test_boolean_data_is_not_a_count(self):
        def wrong_shape(command, path, headers):
            if not headers.get("Authorization"):
                return healthy_server(command, path, headers)
            h, b = json_body({"data": True})
            return 200, h, b
        self.start(wrong_shape)
        self.assertEqual(1, self.run_script(secret=SERVER_SECRET))

    def test_retries_rate_limited_response(self):
        def rate_limited(command, path, headers):
            return 429, {}, b""
        self.start(rate_limited)
        self.assertEqual(1, self.run_script())
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))

    def test_times_out_on_slow_server(self):
        release = threading.Event()

        def slow(command, path, headers):
            release.wait(5)
            return healthy_server(command, path, headers)
        self.start(slow)
        original = smoke_test.REQUEST_TIMEOUT_SECONDS
        smoke_test.REQUEST_TIMEOUT_SECONDS = 0.3
        try:
            self.assertEqual(1, self.run_script())
        finally:
            smoke_test.REQUEST_TIMEOUT_SECONDS = original
            release.set()
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))

    # --- 설정 오류 ---

    def test_refuses_to_send_token_over_plain_http(self):
        env = {"SMOKE_BASE_URL": "http://ono-dev.seungminki.shop", "SMOKE_ACCESS_TOKEN_SECRET": SERVER_SECRET}
        self.assertEqual(2, smoke_test.run(env, sleep=self.sleeps.append))

    def test_config_error_without_base_url(self):
        self.assertEqual(2, smoke_test.run({}, sleep=self.sleeps.append))

    def test_fails_on_non_base64_secret(self):
        self.start(healthy_server)
        self.assertEqual(1, self.run_script(secret="not base64 !!"))
        self.assertEqual(1, len(self.server.requests))


if __name__ == "__main__":
    unittest.main()
