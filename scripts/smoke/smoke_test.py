#!/usr/bin/env python3
"""OnO 스모크 테스트.

배포된 서버가 사용자를 받을 수 있는 상태인지 실제 도메인으로 확인한다.
컨테이너 healthcheck(actuator/health)는 프로세스가 떴는지만 알려 주고,
Cloudflare 와 nginx, JWT 서명 키, DB 조회는 거치지 않는다. 그 사이를 여기서 본다.

운영 서버에 영향을 주지 않는 것이 제일 중요한 조건이라 아래를 지킨다.

- GET 만 보낸다. 호출하는 경로는 SAFE_PATH 하나로 고정되어 있고 바꿀 수 없다.
- SAFE_PATH 는 읽기 전용 트랜잭션에서 count 쿼리 하나만 돈다
  (ProblemService.findProblemCountByUser, @Transactional(readOnly = true)).
  GET /api/users 는 조회처럼 보이지만 로그인 미션 기록과 마지막 접속 시각 갱신이
  같이 돌아서(UserController.getUserInfo) 쓰지 않는다.
- 인증 확인용 토큰은 존재하지 않는 사용자 ID 0 으로 60초짜리를 만든다.
  JwtTokenFilter 는 서명과 블랙리스트만 보고 사용자 존재 여부는 보지 않으며,
  count 쿼리는 행이 없으면 0 을 돌려준다. 그래서 운영 DB 에 스모크 계정을 만들 필요가 없고
  실제 사용자의 데이터에는 닿지 않는다.
- 한 번 실행에 보내는 요청은 최대 (확인 2개 x 시도 3번) = 6개다.
  재시도는 연결 실패와 5xx, 429 에서만 하고 4xx 는 바로 판정한다.
- 리다이렉트를 따라가지 않는다. urllib 은 기본으로 3xx 를 따라가면서 Authorization 헤더까지
  옮기기 때문에, 따라가면 토큰이 다른 호스트로 새고 요청 수 상한도 깨진다. 3xx 는 실패로 본다.
- 토큰은 https 이거나 로컬 주소일 때만 보낸다.

환경 변수
  SMOKE_BASE_URL             필수. 예: https://ono-dev.seungminki.shop
  SMOKE_ACCESS_TOKEN_SECRET  선택. 서버의 jwt.accessToken.secret 과 같은 Base64 값.
                             없으면 인증 확인은 건너뛰고 도달 확인만 한다.

종료 코드: 0 통과, 1 실패, 2 설정 오류
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Callable, Mapping

SAFE_PATH = "/api/problems/problemCount"

# 존재하지 않는 사용자. 위 docstring 참고.
SMOKE_USER_ID = "0"
SMOKE_AUTHORITY = "ROLE_GUEST"
TOKEN_TTL_SECONDS = 60

REQUEST_TIMEOUT_SECONDS = 10
MAX_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 3
USER_AGENT = "OnO-SmokeTest/1.0"

# AuthErrorCase
AUTHENTICATION_FAILED = 1007
ACCESS_TOKEN_EXPIRED = 1005
INVALID_ACCESS_TOKEN = 1009

LOCAL_HOSTS = ("127.0.0.1", "localhost", "::1")


@dataclass
class Response:
    status: int | None
    headers: Mapping[str, str] = field(default_factory=dict)
    body: bytes = b""
    error: str | None = None
    elapsed_ms: int = 0
    attempts: int = 1

    def json(self):
        try:
            return json.loads(self.body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return None

    def snippet(self) -> str:
        text = self.body.decode("utf-8", errors="replace").strip().replace("\n", " ")
        return text[:120]


@dataclass
class CheckResult:
    name: str
    passed: bool
    detail: str
    elapsed_ms: int = 0
    skipped: bool = False


def _b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def mint_access_token(secret_b64: str, now: int) -> str:
    """JwtTokenizer.createAccessToken 과 같은 모양의 HS256 토큰을 만든다.

    서버는 jwt.accessToken.secret 을 Base64 로 디코딩한 바이트를 HMAC 키로 쓴다.
    """
    key = base64.b64decode(secret_b64.strip(), validate=True)
    header = {"alg": "HS256"}
    payload = {
        "authority": SMOKE_AUTHORITY,
        "sub": SMOKE_USER_ID,
        "iat": now,
        "exp": now + TOKEN_TTL_SECONDS,
    }
    signing_input = (
        _b64url(json.dumps(header, separators=(",", ":")).encode())
        + "."
        + _b64url(json.dumps(payload, separators=(",", ":")).encode())
    )
    signature = hmac.new(key, signing_input.encode("ascii"), hashlib.sha256).digest()
    return signing_input + "." + _b64url(signature)


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


def _get_once(url: str, headers: Mapping[str, str]) -> Response:
    request = urllib.request.Request(url, method="GET", headers={"User-Agent": USER_AGENT, **headers})
    started = time.monotonic()
    try:
        with _OPENER.open(request, timeout=REQUEST_TIMEOUT_SECONDS) as res:
            body = res.read()
            return Response(res.status, dict(res.headers), body,
                            elapsed_ms=int((time.monotonic() - started) * 1000))
    except urllib.error.HTTPError as e:
        body = e.read()
        return Response(e.code, dict(e.headers or {}), body,
                        elapsed_ms=int((time.monotonic() - started) * 1000))
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        reason = getattr(e, "reason", e)
        return Response(None, error=str(reason), elapsed_ms=int((time.monotonic() - started) * 1000))


def _should_retry(res: Response) -> bool:
    return res.status is None or res.status == 429 or res.status >= 500


def http_get(base_url: str, headers: Mapping[str, str], sleep: Callable[[float], None]) -> Response:
    """SAFE_PATH 로 GET 을 보낸다. 이 스크립트에서 요청을 보내는 곳은 여기 하나다."""
    url = base_url.rstrip("/") + SAFE_PATH
    res = _get_once(url, headers)
    attempts = 1
    while _should_retry(res) and attempts < MAX_ATTEMPTS:
        sleep(RETRY_DELAY_SECONDS)
        res = _get_once(url, headers)
        attempts += 1
    res.attempts = attempts
    return res


def _lower_headers(res: Response) -> dict[str, str]:
    return {k.lower(): v for k, v in res.headers.items()}


def _describe_unexpected(res: Response) -> str:
    if res.status is None:
        return f"서버에 닿지 않았습니다 ({res.error}, {res.attempts}번 시도)"
    headers = _lower_headers(res)
    if 300 <= res.status < 400:
        return (f"리다이렉트 응답입니다 (HTTP {res.status}, Location: {headers.get('location', '-')}). "
                "따라가지 않았습니다. 점검 모드나 nginx 설정을 확인해야 합니다")
    if "cf-mitigated" in headers or (res.status == 403 and "cloudflare" in headers.get("server", "").lower()
                                     and res.json() is None):
        return f"Cloudflare 가 요청을 막았습니다 (HTTP {res.status}). 앱까지 가지 않아서 서버 상태는 알 수 없습니다"
    if res.status >= 500 and "cloudflare" in headers.get("server", "").lower() and res.json() is None:
        # 원 서버 응답이면 nginx 나 앱이 만든 본문이 온다. Cloudflare 가 직접 만든 5xx 는
        # 원 서버(맥미니 nginx)까지 닿지 못했다는 뜻이라 앱 오류와 구분해서 알린다.
        return (f"Cloudflare 가 원 서버에 닿지 못했습니다 (HTTP {res.status}, {res.attempts}번 시도). "
                "서버나 nginx 가 꺼져 있거나 네트워크가 끊겼을 수 있습니다")
    if res.status >= 500:
        return f"앱이 정상 응답하지 않습니다 (HTTP {res.status}, {res.attempts}번 시도): {res.snippet()}"
    if res.json() is None:
        return (f"JSON 이 아닌 응답입니다 (HTTP {res.status}). "
                f"nginx 오류 페이지나 점검 페이지일 수 있습니다: {res.snippet()}")
    return f"예상과 다른 응답입니다 (HTTP {res.status}): {res.snippet()}"


def check_reachable(base_url: str, sleep: Callable[[float], None]) -> CheckResult:
    """토큰 없이 호출해서 Cloudflare, nginx, 앱의 인증 필터까지 닿는지 본다. DB 는 거치지 않는다."""
    name = "앱까지 닿는가 (토큰 없이 401)"
    res = http_get(base_url, {}, sleep)
    body = res.json()
    if res.status == 401 and isinstance(body, dict) and body.get("errorCode") == AUTHENTICATION_FAILED:
        return CheckResult(name, True, f"HTTP 401, errorCode {AUTHENTICATION_FAILED}", res.elapsed_ms)
    if res.status == 200:
        return CheckResult(name, False,
                           "토큰 없이 200 이 나왔습니다. 인증 설정이 풀렸는지 확인이 필요합니다", res.elapsed_ms)
    return CheckResult(name, False, _describe_unexpected(res), res.elapsed_ms)


def check_authenticated(base_url: str, secret_b64: str, now: int,
                        sleep: Callable[[float], None]) -> CheckResult:
    """스모크 토큰으로 호출해서 JWT 서명 키와 DB 조회가 맞는지 본다."""
    name = "인증과 DB 조회가 되는가 (토큰으로 200)"
    try:
        token = mint_access_token(secret_b64, now)
    except (binascii.Error, ValueError):
        return CheckResult(name, False, "SMOKE_ACCESS_TOKEN_SECRET 이 Base64 값이 아닙니다")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::add-mask::{token}")

    res = http_get(base_url, {"Authorization": f"Bearer {token}"}, sleep)
    body = res.json()
    data = body.get("data") if isinstance(body, dict) else None
    if res.status == 200 and isinstance(data, int) and not isinstance(data, bool):
        return CheckResult(name, True, "HTTP 200, count 조회 응답", res.elapsed_ms)
    if res.status == 401 and isinstance(body, dict):
        # 서버 버전에 따라 같은 원인에도 코드가 다르게 나와서 가능한 원인을 같이 적는다.
        # main(2026-07-01)은 서명이 틀려도 1005 를 주고, develop 은 1009 를 준다.
        # 1007 은 블랙리스트 조회(Redis) 실패처럼 토큰과 무관한 예외에서도 나온다.
        causes = {
            INVALID_ACCESS_TOKEN: "SMOKE_ACCESS_TOKEN_SECRET 이 서버의 JWT 서명 키와 다를 수 있습니다",
            ACCESS_TOKEN_EXPIRED: "서명 키가 다르거나(구버전 서버는 이때도 1005 를 줍니다) 러너와 서버의 시계가 어긋났을 수 있습니다",
            AUTHENTICATION_FAILED: "서명 키가 다르거나 서버의 Redis(토큰 블랙리스트 조회)에 문제가 있을 수 있습니다",
        }
        code = body.get("errorCode")
        if code in causes:
            return CheckResult(name, False, f"토큰이 거절됐습니다 (errorCode {code}). {causes[code]}", res.elapsed_ms)
    return CheckResult(name, False, _describe_unexpected(res), res.elapsed_ms)


def run(env: Mapping[str, str], sleep: Callable[[float], None] = time.sleep,
        clock: Callable[[], float] = time.time) -> int:
    base_url = env.get("SMOKE_BASE_URL", "").strip()
    if not base_url.startswith(("https://", "http://")):
        print("SMOKE_BASE_URL 이 없거나 http(s):// 로 시작하지 않습니다", file=sys.stderr)
        return 2
    secret = env.get("SMOKE_ACCESS_TOKEN_SECRET", "").strip()
    host = urllib.parse.urlsplit(base_url).hostname or ""
    if secret and base_url.startswith("http://") and host not in LOCAL_HOSTS:
        print("토큰을 평문으로 보내지 않도록 로컬 주소가 아니면 https:// 만 받습니다", file=sys.stderr)
        return 2

    print(f"대상: {base_url}{SAFE_PATH} (GET 만 보냅니다)")
    results = [check_reachable(base_url, sleep)]

    auth_name = "인증과 DB 조회가 되는가 (토큰으로 200)"
    if not results[0].passed:
        results.append(CheckResult(auth_name, False, "앞 확인이 실패해서 요청을 보내지 않았습니다", skipped=True))
    elif not secret:
        results.append(CheckResult(auth_name, True, "SMOKE_ACCESS_TOKEN_SECRET 이 없어 건너뛰었습니다", skipped=True))
    else:
        results.append(check_authenticated(base_url, secret, int(clock()), sleep))

    failed = [r for r in results if not r.passed]
    for r in results:
        mark = "건너뜀" if r.skipped else ("통과" if r.passed else "실패")
        elapsed = f" ({r.elapsed_ms}ms)" if r.elapsed_ms else ""
        print(f"[{mark}] {r.name}{elapsed}: {r.detail}")
    _write_step_summary(env, base_url, results)

    print("스모크 테스트 실패" if failed else "스모크 테스트 통과")
    return 1 if failed else 0


def _write_step_summary(env: Mapping[str, str], base_url: str, results: list[CheckResult]) -> None:
    path = env.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = [
        "## 스모크 테스트",
        "",
        f"- 대상: `{base_url}` (GET `{SAFE_PATH}` 만 보냅니다)",
        "",
        "| 확인 | 결과 | 응답 시간 | 내용 |",
        "|---|---|---:|---|",
    ]
    for r in results:
        mark = "건너뜀" if r.skipped else ("✅ 통과" if r.passed else "❌ 실패")
        elapsed = f"{r.elapsed_ms}ms" if r.elapsed_ms else "-"
        lines.append(f"| {r.name} | {mark} | {elapsed} | {r.detail.replace('|', '/')} |")
    with open(path, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    sys.exit(run(os.environ))
