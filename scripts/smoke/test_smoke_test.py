"""smoke_test.py 자체 테스트.

운영 서버에 요청을 보내기 전에 스크립트가 틀리지 않았는지 확인하려고 둔다.
스모크 테스트 워크플로가 실제 요청보다 먼저 이 테스트를 돌린다.

OnO 서버가 스모크 테스트에 응답하는 방식을 흉내 내는 가짜 서버를 띄우고, 받은 요청을 전부 기록해서
"허용한 요청만, 정해진 횟수 안에서, 스모크 흔적만 지우는지"까지 확인한다.

실행: python3 -m unittest discover -s scripts/smoke -p 'test_*.py' -v
"""

import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import smoke_test  # noqa: E402

SERVER_SECRET = base64.b64encode(b"s" * 64).decode()
OTHER_SECRET = base64.b64encode(b"x" * 64).decode()
REFRESH_SECRET = base64.b64encode(b"r" * 64).decode()
NOW = 1_800_000_000
SMOKE_USER = "42"
REAL_USER = "7"


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


def issued_token(user_id: str) -> str:
    """서버가 게스트 가입 때 발급하는 토큰 모양. 앞에 Bearer 가 붙어 있다."""
    return "Bearer " + smoke_test.mint_access_token(SERVER_SECRET, NOW, user_id)


class FakeOnO:
    """스모크 테스트가 부르는 API 만 흉내 내는 OnO 서버.

    사용자마다 루트 폴더와 하위 폴더, 복습노트, 오답노트를 들고 있고, 소유권과 에러 코드를 실제 서버처럼 돌려준다.
    refresh token 은 서버가 발급해 저장한 것만 갱신되고(stored_refresh_tokens), 나머지는 1002 다.
    override(method, path_regex) 로 특정 요청의 응답을 바꿔 실패 상황을 만든다.
    """

    def __init__(self):
        self.requests = []
        self.overrides = []
        self.lock = threading.Lock()
        self.next_id = 100
        self.users = {}
        self.stored_refresh_tokens = set()
        self.now = NOW  # 만료 판정 기준. 스크립트를 별도 프로세스로 돌리는 시험은 실제 시각으로 바꾼다
        self.add_user(SMOKE_USER, marker=True)
        self.add_user(REAL_USER, marker=False, extra_folders=["수학", "__smoke_run_looks_like_but_real_user"])
        self.add_problem(REAL_USER, "__smoke_run_looks_like_but_real_user")
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def _handle(self):
                length = int(self.headers.get("Content-Length") or 0)
                raw = self.rfile.read(length) if length else b""
                body = json.loads(raw) if raw else None
                outer.requests.append((self.command, self.path, dict(self.headers), body))
                status, headers, payload = outer.dispatch(self.command, self.path, dict(self.headers), body)
                headers = dict(headers)
                server = headers.pop("Server", None)
                if server:
                    self.version_string = lambda: server
                try:
                    self.send_response(status)
                    for k, v in headers.items():
                        self.send_header(k, v)
                    self.end_headers()
                    self.wfile.write(payload)
                except (BrokenPipeError, ConnectionResetError):
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

    # 상태
    def new_id(self):
        with self.lock:
            self.next_id += 1
            return self.next_id

    def add_user(self, user_id, marker, extra_folders=()):
        root = self.new_id()
        folders = {root: {"folderId": root, "folderName": "책장", "parent": None, "deleted": False}}
        names = ["공책"] + (["__smoke_account__"] if marker else []) + list(extra_folders)
        for name in names:
            fid = self.new_id()
            folders[fid] = {"folderId": fid, "folderName": name, "parent": root, "deleted": False}
        nid = self.new_id()
        notes = {nid: {"practiceNoteId": nid, "practiceTitle": "복습 세트", "deleted": False}}
        self.users[user_id] = {"root": root, "folders": folders, "notes": notes, "problems": {}}
        self.add_problem(user_id, "근의 공식")

    def add_problem(self, user_id, memo):
        user = self.users[user_id]
        pid = self.new_id()
        user["problems"][pid] = {"problemId": pid, "memo": memo, "folderId": user["root"], "deleted": False}
        return pid

    def live_folder_names(self, user_id):
        return sorted(f["folderName"] for f in self.users[user_id]["folders"].values() if not f["deleted"])

    def live_note_titles(self, user_id):
        return sorted(n["practiceTitle"] for n in self.users[user_id]["notes"].values() if not n["deleted"])

    def live_problem_memos(self, user_id):
        return sorted(p["memo"] for p in self.users[user_id]["problems"].values() if not p["deleted"])

    def override(self, method, path_regex, response, times=None):
        self.overrides.append({"method": method, "path": path_regex, "response": response, "times": times})

    def paths(self, method=None):
        return [(m, p.split("?")[0]) for m, p, _, _ in self.requests if method is None or m == method]

    # 응답
    def dispatch(self, method, full_path, headers, body):
        path = full_path.split("?")[0]
        for o in self.overrides:
            if o["method"] == method and re.fullmatch(o["path"], path) and o["times"] != 0:
                if o["times"] is not None:
                    o["times"] -= 1
                resp = o["response"]
                return resp(method, path, headers, body) if callable(resp) else resp

        if method == "POST" and path == "/api/auth/signup/guest":
            uid = str(self.new_id())
            self.add_user(uid, marker=False)
            return ok({"accessToken": issued_token(uid), "refreshToken": "refresh-" + uid})

        if method == "POST" and path == "/api/auth/refresh":
            # JwtTokenService.refreshAccessToken: 서명과 만료를 보고, 저장된 토큰인지 찾는다.
            token = (body or {}).get("refreshToken") or ""
            try:
                claims = verify_token(token, REFRESH_SECRET)
            except ValueError:  # 형식이 깨진 토큰. binascii.Error 도 ValueError 다
                claims = None
            if claims is None:
                return error(400, 1001)
            if claims["exp"] < self.now:
                return error(401, 1006)
            if token not in self.stored_refresh_tokens:
                return error(401, 1002)
            return ok({"accessToken": "Bearer new", "refreshToken": "new"})

        auth = headers.get("Authorization")
        if not auth:
            return error(401, 1007)
        claims = verify_token(auth.removeprefix("Bearer "), SERVER_SECRET)
        if claims is None:
            return error(401, 1009)
        uid = claims["sub"]
        if uid not in self.users:
            if method == "GET" and path == "/api/problems/problemCount":
                return ok(0)
            return error(404, 5001)
        user = self.users[uid]
        folders, notes, problems = user["folders"], user["notes"], user["problems"]

        def folder_view(f):
            parent = folders.get(f["parent"])
            subs = [{"folderId": s["folderId"], "folderName": s["folderName"]}
                    for s in folders.values() if s["parent"] == f["folderId"] and not s["deleted"]]
            return {"folderId": f["folderId"], "folderName": f["folderName"], "subFolderList": subs, "userId": int(uid),
                    "parentFolder": {"folderId": parent["folderId"], "folderName": parent["folderName"]} if parent else None}

        page = {"content": [], "hasNext": False, "nextCursor": None, "size": 20}
        m = re.fullmatch(r"/api/folders/(\d+)", path)
        if method == "GET":
            if path == "/api/folders/root":
                return ok(folder_view(folders[user["root"]]))
            if m:
                f = folders.get(int(m.group(1)))
                return ok(folder_view(f)) if f and not f["deleted"] else error(404, 5001)
            pm = re.fullmatch(r"/api/problems/(\d+)", path)
            if pm:
                problem = problems.get(int(pm.group(1)))
                if not problem or problem["deleted"]:
                    return error(404, 4001)
                return ok({k: problem[k] for k in ("problemId", "memo", "folderId")})
            if path == "/api/fileUpload/presigned-urls":
                return ok([{"presignedUrl": "https://bucket.s3.amazonaws.com/x.png?X-Amz-Signature=secretsig",
                            "fileUrl": "https://bucket.s3.amazonaws.com/x.png"}])
            n = re.fullmatch(r"/api/practiceNotes/(\d+)", path)
            if n:
                note = notes.get(int(n.group(1)))
                if not note or note["deleted"]:
                    return error(404, 6001)
                return ok({"practiceNoteId": note["practiceNoteId"], "practiceTitle": note["practiceTitle"],
                           "practiceNotification": None})
            simple = {
                "/api/folders": [folder_view(f) for f in folders.values() if not f["deleted"]],
                "/api/folders/thumbnails/V2": page,
                "/api/problems/problemCount": 0,
                "/api/problems/review-due": {"dueCount": 0, "overdueCount": 0, "problems": []},
                "/api/problems/user": [{k: p[k] for k in ("problemId", "memo", "folderId")}
                                       for p in problems.values() if not p["deleted"]],
                "/api/practiceNotes/thumbnail": [{"practiceNoteId": x["practiceNoteId"], "practiceTitle": x["practiceTitle"]}
                                                 for x in notes.values() if not x["deleted"]],
                "/api/practiceNotes/thumbnail/V2": page,
                "/api/practiceNotes/all": [],
                "/api/problem-solves/user/count": 0,
                "/api/study-room": [],
                "/api/tags": [],
                "/api/learning-calendar": {"year": 2027, "month": 1, "records": []},
                "/api/learning-reports/summary": {"monthLabel": "1월"},
            }
            if path in simple:
                return ok(simple[path])
            if re.fullmatch(r"/api/folders/\d+/subfolders/V2|/api/problems/folder/\d+/V2", path):
                return ok(page)
            return error(404, 9999)

        if method == "POST" and path == "/api/folders":
            parent = folders.get(body.get("parentFolderId"))
            if not parent or parent["deleted"]:
                return error(404, 5001)
            fid = self.new_id()
            folders[fid] = {"folderId": fid, "folderName": body["folderName"], "parent": parent["folderId"], "deleted": False}
            return ok(fid)
        if method == "PATCH" and path == "/api/folders":
            f = folders.get(body.get("folderId"))
            if not f or f["deleted"]:
                return error(404, 5001)
            f["folderName"] = body["folderName"]
            return ok("폴더가 성공적으로 수정되었습니다.")
        if method == "DELETE" and path == "/api/folders":
            ids = body.get("deleteFolderIdList", [])
            if any(i not in folders or folders[i]["deleted"] for i in ids):
                return error(404, 5001)
            for i in ids:
                folders[i]["deleted"] = True
            return ok("폴더가 성공적으로 삭제되었습니다.")
        if method == "POST" and path == "/api/practiceNotes":
            nid = self.new_id()
            notes[nid] = {"practiceNoteId": nid, "practiceTitle": body["practiceTitle"], "deleted": False}
            return ok(nid, status=201)
        if method == "DELETE" and path == "/api/practiceNotes":
            ids = body.get("deletePracticeIdList", [])
            if any(i not in notes or notes[i]["deleted"] for i in ids):
                return error(404, 6001)
            for i in ids:
                notes[i]["deleted"] = True
            return ok("선택한 복습 노트가 삭제되었습니다.")
        if method == "POST" and path == "/api/problems/v2":
            folder = folders.get(body.get("folderId"))
            if not folder or folder["deleted"]:
                return error(404, 5001)
            pid = self.new_id()
            problems[pid] = {"problemId": pid, "memo": body.get("memo"), "folderId": folder["folderId"], "deleted": False}
            return ok(pid)
        if method == "DELETE" and path == "/api/problems":
            ids = body.get("deleteProblemIdList", [])
            if any(i not in problems or problems[i]["deleted"] for i in ids):
                return error(404, 4001)
            for i in ids:
                problems[i]["deleted"] = True
            return ok("문제 삭제가 완료되었습니다.")
        return error(404, 9999)


def ok(data, status=200):
    return status, {"Content-Type": "application/json"}, json.dumps({"data": data}).encode()


def error(status, code):
    return status, {"Content-Type": "application/json"}, json.dumps({"errorCode": code, "message": "error"}).encode()


class SmokeTestBase(unittest.TestCase):

    def setUp(self):
        self.sleeps = []
        self.server = FakeOnO()

    def tearDown(self):
        self.server.close()

    def env(self, **overrides):
        env = {"SMOKE_BASE_URL": self.server.url, "SMOKE_LEVEL": "read"}
        env.update({k: v for k, v in overrides.items() if v is not None})
        return env

    def run_smoke(self, level="read", secret=SERVER_SECRET, user_id=SMOKE_USER, **extra):
        env = self.env(SMOKE_LEVEL=level, SMOKE_ACCESS_TOKEN_SECRET=secret, SMOKE_USER_ID=user_id, **extra)
        return smoke_test.run(env, sleep=self.sleeps.append, clock=lambda: NOW)

    def assert_only_allowed_requests(self):
        for method, path in self.server.paths():
            self.assertTrue(any(m == method and re.fullmatch(p, path) for m, p in smoke_test.ALLOWED_REQUESTS),
                            f"허용 목록 밖 요청: {method} {path}")
            self.assertNotEqual("/api/users", path)
        self.assertLessEqual(len(self.server.requests), smoke_test.MAX_REQUESTS)


class ReachTest(SmokeTestBase):

    def test_reach_level_sends_one_request_without_token(self):
        self.assertEqual(0, self.run_smoke(level="reach"))
        self.assertEqual([("GET", smoke_test.PROBE_PATH)], self.server.paths())
        self.assertNotIn("Authorization", self.server.requests[0][2])

    def test_without_secret_only_reach_runs(self):
        self.assertEqual(0, self.run_smoke(secret=None, user_id=None))
        self.assertEqual(1, len(self.server.requests))

    def test_fails_when_endpoint_is_open_without_token(self):
        self.server.override("GET", smoke_test.PROBE_PATH, ok(0))
        self.assertEqual(1, self.run_smoke())
        self.assertEqual(1, len(self.server.requests), "첫 확인이 실패하면 요청을 더 보내지 않는다")

    def test_retries_bad_gateway_then_fails(self):
        self.server.override("GET", smoke_test.PROBE_PATH, (502, {"Content-Type": "text/html"}, b"<html>502</html>"))
        self.assertEqual(1, self.run_smoke())
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))
        self.assertEqual([smoke_test.RETRY_DELAY_SECONDS] * (smoke_test.MAX_ATTEMPTS - 1), self.sleeps)

    def test_recovers_when_first_attempt_fails(self):
        self.server.override("GET", smoke_test.PROBE_PATH, (503, {}, b""), times=1)
        self.assertEqual(0, self.run_smoke(level="reach"))
        self.assertEqual(2, len(self.server.requests))

    def test_reports_origin_unreachable_behind_cloudflare(self):
        # 2026-09-15 dev 도메인에서 실제로 받은 응답 모양이다 (server: cloudflare, 본문 "error code: 502").
        self.server.override("GET", smoke_test.PROBE_PATH,
                             (502, {"Server": "cloudflare", "Content-Type": "text/plain"}, b"error code: 502"))
        smoke = smoke_test.SmokeRun(self.env(), self.sleeps.append, lambda: NOW)
        self.assertFalse(smoke.check_reachable(smoke_test.Client(self.server.url, self.sleeps.append, smoke.budget)))
        self.assertIn("원 서버에 닿지 못했습니다", smoke.results[0].detail)

    def test_reports_cloudflare_block_separately(self):
        self.server.override("GET", smoke_test.PROBE_PATH,
                             (403, {"Server": "cloudflare", "cf-mitigated": "challenge"}, b"<html>Just a moment</html>"))
        self.assertEqual(1, self.run_smoke())

    def test_fails_on_maintenance_page(self):
        self.server.override("GET", smoke_test.PROBE_PATH, (200, {"Content-Type": "text/html"}, b"<html>maintenance</html>"))
        self.assertEqual(1, self.run_smoke())

    def test_does_not_follow_redirect(self):
        self.server.override("GET", smoke_test.PROBE_PATH, (302, {"Location": "/api/users"}, b""))
        self.assertEqual(1, self.run_smoke())
        self.assertEqual([("GET", smoke_test.PROBE_PATH)], self.server.paths())
        self.assertEqual([], self.sleeps, "3xx 는 재시도하지 않는다")

    def test_retries_rate_limited_response(self):
        self.server.override("GET", smoke_test.PROBE_PATH, (429, {}, b""))
        self.assertEqual(1, self.run_smoke())
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.server.requests))

    def test_times_out_on_slow_server(self):
        release = threading.Event()

        def slow(method, path, headers, body):
            release.wait(5)
            return error(401, 1007)
        self.server.override("GET", smoke_test.PROBE_PATH, slow)
        original = smoke_test.REQUEST_TIMEOUT_SECONDS
        smoke_test.REQUEST_TIMEOUT_SECONDS = 0.3
        try:
            self.assertEqual(1, self.run_smoke())
        finally:
            smoke_test.REQUEST_TIMEOUT_SECONDS = original
            release.set()

    def test_fails_when_server_is_unreachable(self):
        url = self.server.url
        self.server.close()
        self.server = FakeOnO()
        env = {"SMOKE_BASE_URL": url}
        self.assertEqual(1, smoke_test.run(env, sleep=self.sleeps.append, clock=lambda: NOW))


class AuthTest(SmokeTestBase):

    def test_without_user_id_checks_count_with_nonexistent_user(self):
        self.assertEqual(0, self.run_smoke(user_id=None))
        self.assertEqual([("GET", smoke_test.PROBE_PATH)] * 2, self.server.paths())
        token = self.server.requests[1][2]["Authorization"].removeprefix("Bearer ")
        self.assertEqual("0", verify_token(token, SERVER_SECRET)["sub"])

    def test_token_for_smoke_user_is_guest_with_short_ttl(self):
        self.run_smoke()
        token = self.server.requests[1][2]["Authorization"].removeprefix("Bearer ")
        claims = verify_token(token, SERVER_SECRET)
        self.assertEqual(SMOKE_USER, claims["sub"])
        self.assertEqual("ROLE_GUEST", claims["authority"])
        self.assertEqual(smoke_test.TOKEN_TTL_SECONDS, claims["exp"] - claims["iat"])

    def test_fails_when_signing_key_differs(self):
        self.assertEqual(1, self.run_smoke(secret=OTHER_SECRET))
        self.assertEqual(2, len(self.server.requests), "토큰이 거절되면 조회를 더 보내지 않는다")

    def test_old_server_reports_wrong_key_as_expired(self):
        # 운영 main(2026-07-01)은 서명이 틀려도 1005 를 준다. 실패로 잡고 서명 키 가능성도 안내해야 한다.
        self.server.override("GET", "/api/folders/root", error(401, 1005))
        smoke = smoke_test.SmokeRun(self.env(SMOKE_ACCESS_TOKEN_SECRET=SERVER_SECRET, SMOKE_USER_ID=SMOKE_USER),
                                    self.sleeps.append, lambda: NOW)
        smoke.execute()
        failed = [r for r in smoke.results if not r.passed and not r.skipped]
        self.assertIn("서명 키", failed[0].detail)

    def test_boolean_data_is_not_a_count(self):
        self.server.override("GET", smoke_test.PROBE_PATH, lambda m, p, h, b: ok(True) if h.get("Authorization") else error(401, 1007))
        self.assertEqual(1, self.run_smoke(user_id=None))


class RefreshTest(SmokeTestBase):

    def refresh_requests(self):
        return [r for r in self.server.requests if r[1] == smoke_test.REFRESH_PATH]

    def test_passes_when_server_answers_not_found(self):
        self.assertEqual(0, self.run_smoke(level="read", SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))
        (method, _, headers, body), = self.refresh_requests()
        self.assertEqual("POST", method)
        self.assertNotIn("Authorization", headers)
        claims = verify_token(body["refreshToken"], REFRESH_SECRET)
        self.assertEqual("0", claims["sub"], "스모크 계정이 아니라 없는 사용자로 만든다")
        self.assertIn("jti", claims)
        self.assertNotIn(body["refreshToken"], self.server.stored_refresh_tokens)
        self.assert_only_allowed_requests()
        self.assertEqual(18, len(self.server.requests))

    def test_runs_even_without_access_secret_or_user(self):
        self.assertEqual(0, self.run_smoke(level="read", secret=None, user_id=None, SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))
        self.assertEqual([("GET", smoke_test.PROBE_PATH), ("POST", smoke_test.REFRESH_PATH)], self.server.paths())

    def test_reach_level_does_not_check_refresh(self):
        self.assertEqual(0, self.run_smoke(level="reach", SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))
        self.assertEqual([], self.refresh_requests())

    def test_fails_when_refresh_key_differs(self):
        smoke = smoke_test.SmokeRun(self.env(SMOKE_REFRESH_TOKEN_SECRET=OTHER_SECRET), self.sleeps.append, lambda: NOW)
        smoke.execute()
        failed = [r for r in smoke.results if not r.passed]
        self.assertEqual(1, len(failed))
        self.assertIn("refresh 서명 키", failed[0].detail)

    def test_fails_when_unknown_token_is_accepted(self):
        self.server.override("POST", smoke_test.REFRESH_PATH, ok({"accessToken": "Bearer a", "refreshToken": "b"}))
        self.assertEqual(1, self.run_smoke(level="read", SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))

    def test_server_error_is_retried_because_nothing_is_written(self):
        self.server.override("POST", smoke_test.REFRESH_PATH, (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.assertEqual(1, self.run_smoke(level="read", SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))
        self.assertEqual(smoke_test.MAX_ATTEMPTS, len(self.refresh_requests()))

    def test_recovers_when_new_container_is_slow_once(self):
        self.server.override("POST", smoke_test.REFRESH_PATH, (503, {}, b""), times=1)
        self.assertEqual(0, self.run_smoke(level="read", SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET))

    def test_refresh_failure_does_not_stop_reads(self):
        self.assertEqual(1, self.run_smoke(level="read", SMOKE_REFRESH_TOKEN_SECRET=OTHER_SECRET))
        self.assertIn(("GET", "/api/learning-reports/summary"), self.server.paths())


class ReadTest(SmokeTestBase):

    def test_read_level_passes_and_sends_only_gets(self):
        self.assertEqual(0, self.run_smoke(level="read"))
        self.assertEqual({"GET"}, {m for m, _ in self.server.paths()})
        self.assert_only_allowed_requests()
        self.assertEqual(17, len(self.server.requests))

    def test_stops_when_account_has_no_marker_folder(self):
        # SMOKE_USER_ID 를 실사용자 ID 로 잘못 넣은 경우. 루트 폴더 한 번만 읽고 멈춘다.
        self.assertEqual(1, self.run_smoke(level="full", user_id=REAL_USER))
        self.assertEqual([("GET", smoke_test.PROBE_PATH), ("GET", "/api/folders/root")], self.server.paths())
        self.assertEqual(self.server.live_folder_names(REAL_USER),
                         sorted(["책장", "공책", "수학", "__smoke_run_looks_like_but_real_user"]))

    def test_stops_when_root_folder_belongs_to_another_user(self):
        real_root = next(iter(self.server.users[REAL_USER]["folders"].values()))
        self.server.override("GET", "/api/folders/root", ok({"folderId": real_root["folderId"], "userId": int(REAL_USER),
                                                             "subFolderList": [{"folderId": 1, "folderName": "__smoke_account__"}]}))
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual(2, len(self.server.requests))

    def test_does_not_leak_root_body_when_shape_is_unexpected(self):
        self.server.override("GET", "/api/folders/root", ok({"secretNote": "실사용자 메모"}))
        with tempfile.TemporaryDirectory() as d:
            summary, output = os.path.join(d, "summary.md"), os.path.join(d, "output.txt")
            self.assertEqual(1, self.run_smoke(level="full", GITHUB_STEP_SUMMARY=summary, GITHUB_OUTPUT=output))
            for path in (summary, output):
                with open(path, encoding="utf-8") as f:
                    self.assertNotIn("실사용자 메모", f.read())

    def test_deadline_stops_the_run(self):
        original = smoke_test.DEADLINE_SECONDS
        smoke_test.DEADLINE_SECONDS = 0
        try:
            self.assertEqual(1, self.run_smoke(level="read"))
        finally:
            smoke_test.DEADLINE_SECONDS = original
        self.assertEqual([], self.server.requests)

    def test_one_failing_read_does_not_stop_other_reads(self):
        self.server.override("GET", "/api/tags", (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.assertEqual(1, self.run_smoke(level="read"))
        self.assertIn(("GET", "/api/learning-reports/summary"), self.server.paths())

    def test_read_shape_mismatch_fails(self):
        self.server.override("GET", "/api/folders/thumbnails/V2", ok([]))
        self.assertEqual(1, self.run_smoke(level="read"))

    def test_writes_summary_and_outputs(self):
        with tempfile.TemporaryDirectory() as d:
            summary, output = os.path.join(d, "summary.md"), os.path.join(d, "output.txt")
            self.server.override("GET", "/api/tags", ok("not a list"))
            self.run_smoke(level="read", GITHUB_STEP_SUMMARY=summary, GITHUB_OUTPUT=output)
            with open(summary, encoding="utf-8") as f:
                self.assertIn("## 스모크 테스트", f.read())
            with open(output, encoding="utf-8") as f:
                text = f.read()
            self.assertIn("failed=1", text)
            self.assertIn("first_failure=조회: 태그", text)


class WriteTest(SmokeTestBase):

    def test_full_level_creates_and_removes_only_its_own_data(self):
        before_folders = self.server.live_folder_names(SMOKE_USER)
        before_notes = self.server.live_note_titles(SMOKE_USER)
        before_problems = self.server.live_problem_memos(SMOKE_USER)
        real_before = (self.server.live_folder_names(REAL_USER), self.server.live_problem_memos(REAL_USER))

        self.assertEqual(0, self.run_smoke(level="full"))

        self.assertEqual(before_folders, self.server.live_folder_names(SMOKE_USER))
        self.assertEqual(before_notes, self.server.live_note_titles(SMOKE_USER))
        self.assertEqual(before_problems, self.server.live_problem_memos(SMOKE_USER))
        self.assertEqual(real_before, (self.server.live_folder_names(REAL_USER), self.server.live_problem_memos(REAL_USER)))
        self.assertIn(("POST", "/api/problems/v2"), self.server.paths())
        self.assert_only_allowed_requests()
        for method, path, headers, body in self.server.requests:
            if method == "POST" and path == "/api/practiceNotes":
                self.assertNotIn("practiceNotification", body, "알림을 붙이면 Quartz 작업이 남는다")
            if method in ("POST", "PATCH", "DELETE"):
                self.assertEqual(SMOKE_USER, verify_token(headers["Authorization"].removeprefix("Bearer "), SERVER_SECRET)["sub"])

    def test_failed_read_blocks_writes(self):
        self.server.override("GET", "/api/problems/review-due", (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual([], self.server.paths("POST") + self.server.paths("PATCH") + self.server.paths("DELETE"))

    def test_server_refusing_delete_keeps_marker_and_reports(self):
        self.server.override("DELETE", "/api/folders", error(400, 5002))
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertIn("__smoke_account__", self.server.live_folder_names(SMOKE_USER))

    def test_read_level_never_writes(self):
        self.run_smoke(level="read")
        self.assertEqual([], self.server.paths("POST") + self.server.paths("PATCH") + self.server.paths("DELETE"))

    def test_cleans_leftovers_but_never_marker_or_other_folders(self):
        user = self.server.users[SMOKE_USER]
        leftover = self.server.new_id()
        user["folders"][leftover] = {"folderId": leftover, "folderName": "__smoke_run_old", "parent": user["root"], "deleted": False}
        note = self.server.new_id()
        user["notes"][note] = {"practiceNoteId": note, "practiceTitle": "__smoke_run_old", "deleted": False}
        self.server.add_problem(SMOKE_USER, "__smoke_run_old")

        self.assertEqual(0, self.run_smoke(level="full"))

        self.assertEqual(["__smoke_account__", "공책", "책장"], self.server.live_folder_names(SMOKE_USER))
        self.assertEqual(["복습 세트"], self.server.live_note_titles(SMOKE_USER))
        self.assertEqual(["근의 공식"], self.server.live_problem_memos(SMOKE_USER))
        deleted_ids = [i for m, p, _, b in self.server.requests if m == "DELETE" for i in
                       (b.get("deleteFolderIdList") or b.get("deletePracticeIdList") or [])]
        marker_id = next(f["folderId"] for f in user["folders"].values() if f["folderName"] == "__smoke_account__")
        self.assertNotIn(marker_id, deleted_ids)
        self.assertNotIn(user["root"], deleted_ids)

    def test_failure_mid_flow_still_cleans_up(self):
        self.server.override("PATCH", "/api/folders", (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.server.override("POST", "/api/practiceNotes", (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual(["__smoke_account__", "공책", "책장"], self.server.live_folder_names(SMOKE_USER))
        self.assertEqual(1, len(self.server.paths("PATCH")), "쓰기는 5xx 여도 재시도하지 않는다")
        self.assertEqual(1, self.server.paths("POST").count(("POST", "/api/practiceNotes")))

    def test_delete_not_reflected_is_reported_and_retried_in_cleanup(self):
        def fake_delete(method, path, headers, body):
            return ok("폴더가 성공적으로 삭제되었습니다.")
        self.server.override("DELETE", "/api/folders", fake_delete, times=1)
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual(2, self.server.paths("DELETE").count(("DELETE", "/api/folders")), "흐름의 삭제 한 번, 마무리 정리 한 번")
        self.assertEqual(["__smoke_account__", "공책", "책장"], self.server.live_folder_names(SMOKE_USER))

    def test_request_budget_stops_the_run(self):
        original = smoke_test.MAX_REQUESTS
        smoke_test.MAX_REQUESTS = 10
        try:
            self.assertEqual(1, self.run_smoke(level="full"))
        finally:
            smoke_test.MAX_REQUESTS = original
        self.assertLessEqual(len(self.server.requests), 10)


class ProblemWriteTest(SmokeTestBase):

    def test_register_sends_new_app_version_so_no_xp_accrues(self):
        self.assertEqual(0, self.run_smoke(level="full"))
        register = [r for r in self.server.requests if r[0] == "POST" and r[1] == "/api/problems/v2"]
        self.assertEqual(1, len(register))
        _, _, headers, body = register[0]
        self.assertEqual(smoke_test.SMOKE_APP_VERSION, headers.get("X-App-Version"))
        self.assertTrue(body["memo"].startswith(smoke_test.RUN_PREFIX))
        self.assertEqual([], body["problemImageUrls"], "이미지를 붙이면 분석 요청과 S3 삭제 메시지가 생길 수 있다")
        others = [r for r in self.server.requests if not (r[0] == "POST" and r[1] == "/api/problems/v2")]
        self.assertTrue(all("X-App-Version" not in h for _, _, h, _ in others), "다른 요청의 동작은 바꾸지 않는다")

    def test_presigned_url_is_not_logged_when_shape_is_unexpected(self):
        self.server.override("GET", "/api/fileUpload/presigned-urls",
                             ok([{"presignedUrl": "http://x/?X-Amz-Signature=secretsig"}]))
        with tempfile.TemporaryDirectory() as d:
            summary, output = os.path.join(d, "summary.md"), os.path.join(d, "output.txt")
            self.assertEqual(1, self.run_smoke(level="full", GITHUB_STEP_SUMMARY=summary, GITHUB_OUTPUT=output))
            for path in (summary, output):
                with open(path, encoding="utf-8") as f:
                    self.assertNotIn("secretsig", f.read())
        self.assertIn(("POST", "/api/problems/v2"), self.server.paths(), "발급 실패는 등록 확인을 막지 않는다")

    def test_presigned_rate_limit_is_reported(self):
        self.server.override("GET", "/api/fileUpload/presigned-urls", error(429, 2005))
        self.assertEqual(1, self.run_smoke(level="full"))
        # 호출마다 카운터가 오르는 GET 이라 재시도하지 않는다.
        self.assertEqual(1, self.server.paths().count(("GET", "/api/fileUpload/presigned-urls")))
        self.assertEqual(["근의 공식"], self.server.live_problem_memos(SMOKE_USER))

    def test_register_failure_stops_flow_without_leftovers(self):
        self.server.override("POST", "/api/problems/v2", (500, {"Content-Type": "application/json"}, b'{"errorCode":9000}'))
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual(1, self.server.paths("POST").count(("POST", "/api/problems/v2")), "쓰기는 재시도하지 않는다")
        self.assertEqual(["근의 공식"], self.server.live_problem_memos(SMOKE_USER))

    def test_delete_refused_is_retried_in_cleanup_and_then_by_next_run(self):
        self.server.override("DELETE", "/api/problems", error(400, 4002), times=2)
        self.assertEqual(1, self.run_smoke(level="full"))
        self.assertEqual(2, self.server.paths("DELETE").count(("DELETE", "/api/problems")), "흐름의 삭제 한 번, 마무리 정리 한 번")
        self.assertEqual(2, len(self.server.live_problem_memos(SMOKE_USER)))

        self.assertEqual(0, self.run_smoke(level="full"), "다음 실행이 흔적을 지운다")
        self.assertEqual(["근의 공식"], self.server.live_problem_memos(SMOKE_USER))

    def test_never_deletes_other_problems(self):
        self.server.add_problem(SMOKE_USER, "smoke 가 들어가지만 흔적 이름은 아님")
        self.assertEqual(0, self.run_smoke(level="full"))
        deleted = [i for m, p, _, b in self.server.requests if m == "DELETE" and p == "/api/problems"
                   for i in b["deleteProblemIdList"]]
        kept = [pid for pid, pr in self.server.users[SMOKE_USER]["problems"].items()
                if not pr["memo"].startswith(smoke_test.RUN_PREFIX)]
        self.assertFalse(set(deleted) & set(kept))


class PreSwitchCheckTest(SmokeTestBase):
    """ci-prod.yml 이 nginx 전환 직전에 부르는 pre_switch_check.sh. docker 는 가짜 명령으로 바꿔 끼운다."""

    HEALTH_UP = ('{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},'
                 '"diskSpace":{"status":"UP","details":{"total":994662584320,"free":123456789}},'
                 '"rabbit":{"status":"UP","details":{"version":"3.13.7"}},"redis":{"status":"UP","details":{"version":"7.4.1"}}}}')

    def run_check(self, health_exit=0, health_body=HEALTH_UP, user_id=SMOKE_USER, fake_python_exit=None):
        script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "pre_switch_check.sh")
        with tempfile.TemporaryDirectory() as d:
            docker = os.path.join(d, "docker")
            body = os.path.join(d, "health.json")
            with open(body, "w", encoding="utf-8") as f:
                f.write(health_body)
            with open(docker, "w", encoding="utf-8") as f:
                f.write(f'#!/bin/sh\necho "$@" >> "{d}/calls"\ncat "{body}"\nexit {health_exit}\n')
            os.chmod(docker, 0o755)
            port = self.server.url.rsplit(":", 1)[1]
            self.server.now = int(time.time())
            env = {k: v for k, v in os.environ.items() if not k.startswith(("SMOKE_", "GITHUB_"))}
            env.update(DOCKER=docker, SMOKE_ACCESS_TOKEN_SECRET=SERVER_SECRET, SMOKE_REFRESH_TOKEN_SECRET=REFRESH_SECRET)
            if fake_python_exit is not None:
                # 버전 확인에서 실패하는 python3 를 PATH 맨 앞에 둔다. 3.9 보다 오래된 python3 가 있는 러너와 같다.
                with open(os.path.join(d, "python3"), "w", encoding="utf-8") as f:
                    f.write(f"#!/bin/sh\nexit {fake_python_exit}\n")
                os.chmod(os.path.join(d, "python3"), 0o755)
                env["PATH"] = d + os.pathsep + env.get("PATH", "")
            if user_id:
                env["SMOKE_USER_ID"] = user_id
            done = subprocess.run(["bash", script, "ono-app-prod-green", port], env=env,
                                  capture_output=True, text=True, timeout=120)
            with open(os.path.join(d, "calls"), encoding="utf-8") as f:
                calls = f.read()
        return done, calls

    def test_passes_and_runs_read_level_against_the_new_port(self):
        done, calls = self.run_check()
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn("exec ono-app-prod-green wget", calls)
        self.assertIn("db UP, diskSpace UP, rabbit UP, redis UP", done.stdout)
        self.assertIn("(단계: read)", done.stdout)
        self.assertEqual(set(), {m for m, _ in self.server.paths()} - {"GET", "POST"})
        self.assertEqual(["/api/auth/refresh"], [p for m, p in self.server.paths() if m == "POST"])

    def test_does_not_print_health_details(self):
        done, _ = self.run_check()
        for secret_ish in ("994662584320", "3.13.7", "isValid()"):
            self.assertNotIn(secret_ish, done.stdout + done.stderr)

    def test_fails_without_smoke_when_health_is_down(self):
        done, _ = self.run_check(health_exit=1, health_body='{"status":"DOWN"}')
        self.assertNotEqual(0, done.returncode)
        self.assertEqual([], self.server.requests, "health 가 실패하면 앱에 요청을 보내지 않는다")

    def test_fails_when_smoke_fails(self):
        done, _ = self.run_check(user_id=REAL_USER)
        self.assertNotEqual(0, done.returncode)
        self.assertIn("스모크 테스트 실패", done.stdout)

    def test_skips_smoke_with_warning_when_python_is_too_old(self):
        done, _ = self.run_check(fake_python_exit=1)
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn("::warning::", done.stdout)
        self.assertEqual([], self.server.requests, "스모크를 건너뛰었으니 앱에 요청하지 않는다")

    def test_passes_when_health_details_are_hidden(self):
        done, _ = self.run_check(health_body='{"status":"UP"}')
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn("show-details 가 꺼져", done.stdout)


class ClientGuardTest(SmokeTestBase):

    def client(self, **kwargs):
        return smoke_test.Client(self.server.url, self.sleeps.append, smoke_test.Budget(), **kwargs)

    def test_blocks_requests_outside_allowlist_before_sending(self):
        client = self.client(token="Bearer x", allow_writes=True)
        for method, path in [("GET", "/api/users"), ("PATCH", "/api/users"), ("DELETE", "/api/users"),
                             ("POST", "/api/auth/logout"), ("POST", "/api/problems"), ("DELETE", "/api/problems/all"),
                             ("POST", "/api/problems/1/analysis"), ("POST", "/api/fileUpload/image"),
                             ("GET", "/actuator/health")]:
            with self.assertRaises(smoke_test.SmokeAbort):
                client.request(method, path)
        self.assertEqual([], self.server.requests)

    def test_blocks_writes_unless_full_level(self):
        with self.assertRaises(smoke_test.SmokeAbort):
            self.client(token="Bearer x").request("POST", "/api/folders", {"folderName": "x"})
        self.assertEqual([], self.server.requests)

    def test_refresh_is_the_only_write_method_allowed_below_full(self):
        client = self.client()
        probe = smoke_test.mint_refresh_token(REFRESH_SECRET, NOW)
        self.assertEqual(401, client.request("POST", smoke_test.REFRESH_PATH, {"refreshToken": probe}, auth=False).status)
        for method, path in [("POST", "/api/problems/v2"), ("DELETE", "/api/problems"),
                             ("GET", "/api/fileUpload/presigned-urls")]:
            with self.assertRaises(smoke_test.SmokeAbort):
                client.request(method, path, {})
        self.assertEqual(1, len(self.server.requests))

    def test_refresh_only_accepts_probe_tokens(self):
        # 서버에 저장된 진짜 토큰을 보내면 회전(쓰기)이 일어난다. 실제 사용자로 서명된 토큰, 형식이 다른 값, 인증 헤더를 막는다.
        real_user_token = smoke_test._sign(REFRESH_SECRET, {"authority": "ROLE_GUEST", "sub": SMOKE_USER,
                                                             "iat": NOW, "exp": NOW + 60})
        client = self.client(token="Bearer x", allow_writes=True)
        for body, auth in [({"refreshToken": real_user_token}, False), ({"refreshToken": "refresh-42"}, False),
                           ({}, False), ({"refreshToken": smoke_test.mint_refresh_token(REFRESH_SECRET, NOW)}, True)]:
            with self.assertRaises(smoke_test.SmokeAbort):
                client.request("POST", smoke_test.REFRESH_PATH, body, auth=auth)
        self.assertEqual([], self.server.requests)

    def test_blocks_signup_outside_bootstrap(self):
        with self.assertRaises(smoke_test.SmokeAbort):
            self.client(allow_writes=True).request("POST", smoke_test.SIGNUP_PATH)
        self.assertEqual([], self.server.requests)

    def test_refuses_to_send_token_over_plain_http(self):
        env = {"SMOKE_BASE_URL": "http://ono-dev.seungminki.shop", "SMOKE_ACCESS_TOKEN_SECRET": SERVER_SECRET}
        self.assertEqual(2, smoke_test.run(env, sleep=self.sleeps.append))

    def test_config_errors(self):
        self.assertEqual(2, smoke_test.run({}, sleep=self.sleeps.append))
        self.assertEqual(2, smoke_test.run(self.env(SMOKE_LEVEL="everything"), sleep=self.sleeps.append))
        self.assertEqual(2, smoke_test.run(self.env(SMOKE_USER_ID="abc"), sleep=self.sleeps.append))
        self.assertEqual(2, smoke_test.run(self.env(SMOKE_ACCESS_TOKEN_SECRET="not base64 !!"), sleep=self.sleeps.append))
        self.assertEqual(2, smoke_test.run(self.env(SMOKE_REFRESH_TOKEN_SECRET="not base64 !!"), sleep=self.sleeps.append))
        self.assertEqual(2, smoke_test.run({"SMOKE_BASE_URL": "http://ono-dev.seungminki.shop",
                                            "SMOKE_REFRESH_TOKEN_SECRET": REFRESH_SECRET}, sleep=self.sleeps.append))
        self.assertEqual([], self.server.requests)


class BootstrapTest(SmokeTestBase):

    def test_creates_guest_with_marker_folder_and_prints_id(self):
        with tempfile.TemporaryDirectory() as d:
            summary = os.path.join(d, "summary.md")
            self.assertEqual(0, smoke_test.bootstrap({"SMOKE_BASE_URL": self.server.url, "GITHUB_STEP_SUMMARY": summary},
                                                     sleep=self.sleeps.append))
            with open(summary, encoding="utf-8") as f:
                text = f.read()
        new_ids = [u for u in self.server.users if u not in (SMOKE_USER, REAL_USER)]
        self.assertEqual(1, len(new_ids))
        self.assertIn("__smoke_account__", self.server.live_folder_names(new_ids[0]))
        self.assertIn(new_ids[0], text)
        self.assertEqual(["POST", "GET", "POST"], [m for m, _ in self.server.paths()])

    def test_new_account_then_passes_full_level(self):
        smoke_test.bootstrap({"SMOKE_BASE_URL": self.server.url}, sleep=self.sleeps.append)
        new_id = next(u for u in self.server.users if u not in (SMOKE_USER, REAL_USER))
        self.assertEqual(0, self.run_smoke(level="full", user_id=new_id))

    def test_refuses_when_account_already_configured(self):
        self.assertEqual(2, smoke_test.bootstrap({"SMOKE_BASE_URL": self.server.url, "SMOKE_USER_ID": SMOKE_USER},
                                                 sleep=self.sleeps.append))
        self.assertEqual([], self.server.requests)

    def test_signup_is_not_retried(self):
        self.server.override("POST", smoke_test.SIGNUP_PATH, (502, {}, b""))
        self.assertEqual(1, smoke_test.bootstrap({"SMOKE_BASE_URL": self.server.url}, sleep=self.sleeps.append))
        self.assertEqual(1, len(self.server.requests))


if __name__ == "__main__":
    unittest.main()
