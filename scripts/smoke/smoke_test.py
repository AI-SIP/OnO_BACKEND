#!/usr/bin/env python3
"""OnO 스모크 테스트.

배포된 서버가 사용자를 받을 수 있는 상태인지 실제 도메인으로 확인한다.
컨테이너 healthcheck(actuator/health)는 프로세스가 떴는지만 알려 주고,
Cloudflare 와 nginx, JWT 서명 키, DB 조회는 거치지 않는다. 그 사이를 여기서 본다.

확인은 세 단계로 나뉜다. 뒤 단계는 앞 단계를 포함한다.

  reach  토큰 없이 한 번 불러서 앱까지 닿는지 본다.
  read   스모크 전용 게스트 계정으로 앱 화면들이 쓰는 조회 API 를 부른다.
  full   read 에 더해, 스모크 계정 안에서 폴더와 복습노트, 오답노트를 만들고 조회하고 지운다.

refresh 서명 키가 있으면 reach 다음에 토큰 갱신 경로도 본다. 서명 키로 직접 만든 refresh token 은
DB 에 없어서 서버는 항상 1002 를 돌려주고 아무것도 쓰지 않는다. 1001 이 오면 서명 키가 어긋난 것이다.

운영 서버에 지장을 주지 않는 것이 제일 중요한 조건이라 아래를 지킨다.
각 API 를 고른 근거는 README.md 에 있다.

- 보낼 수 있는 요청은 ALLOWED_REQUESTS 에 적힌 메서드와 경로뿐이다. 그 밖의 요청은 보내기 전에 막는다.
  GET /api/users 는 조회처럼 보이지만 로그인 미션 기록과 접속 시각 갱신, DAU 집계가 같이 돌아서 넣지 않았다.
- 쓰기 요청은 full 단계에서만 허용하고, 재시도하지 않는다. 토큰 갱신(POST)만 예외인데, 서버가 쓰기 전에 1002 로 끝난다.
- 지우는 것은 이번 실행에서 만든 것과, 이름이 RUN_PREFIX 로 시작하는 지난 실행의 흔적뿐이다.
- 스모크 계정 루트 폴더 아래에 MARKER_FOLDER_NAME 폴더가 없으면 스모크 계정이 아니라고 보고
  더 이상 요청하지 않는다. SMOKE_USER_ID 를 잘못 넣어 실사용자 계정을 건드리는 일을 막는다.
- 한 번 실행에 보내는 요청 수는 MAX_REQUESTS 를 넘지 않고, 전체 시간은 DEADLINE_SECONDS 를 넘지 않는다.
  재시도도 여기에 포함된다. 서버가 느릴 때 스모크 테스트가 요청을 오래 붙들고 있지 않게 한다.
- 조회가 하나라도 실패하면 쓰기 흐름을 시작하지 않는다. 서버가 불안정할 때 쓰기를 더하지 않는다.
- 리다이렉트를 따라가지 않는다. urllib 은 기본으로 3xx 를 따라가면서 Authorization 헤더까지
  옮기기 때문에, 따라가면 토큰이 다른 호스트로 샌다.
- 토큰은 https 이거나 로컬 주소일 때만 보낸다.

환경 변수
  SMOKE_BASE_URL             필수. 예: https://ono-prod.seungminki.shop
  SMOKE_LEVEL                reach | read | full. 기본 read
  SMOKE_ACCESS_TOKEN_SECRET  서버의 jwt.accessToken.secret 과 같은 Base64 값
  SMOKE_REFRESH_TOKEN_SECRET 서버의 jwt.refreshToken.secret 과 같은 Base64 값. 없으면 토큰 갱신 확인만 건너뛴다
  SMOKE_USER_ID              bootstrap 으로 만든 스모크 게스트 계정 ID

  시크릿이 없으면 reach 만 한다. 시크릿만 있고 계정 ID 가 없으면 존재하지 않는 사용자 ID 0 으로
  인증과 count 조회 한 번만 확인한다.

명령
  python3 smoke_test.py             확인
  python3 smoke_test.py bootstrap   스모크 게스트 계정을 한 번 만든다 (Discord 가입 알림이 한 번 간다)

종료 코드: 0 통과, 1 실패, 2 설정 오류
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Callable, Mapping

PROBE_PATH = "/api/problems/problemCount"

# 인증 확인만 할 때 쓰는 존재하지 않는 사용자.
# JwtTokenFilter 는 서명과 블랙리스트만 보고 사용자 존재 여부는 보지 않으며, count 는 0 을 돌려준다.
NONEXISTENT_USER_ID = "0"
SMOKE_AUTHORITY = "ROLE_GUEST"
TOKEN_TTL_SECONDS = 300

MARKER_FOLDER_NAME = "__smoke_account__"
RUN_PREFIX = "__smoke_run_"

REQUEST_TIMEOUT_SECONDS = 10
MAX_ATTEMPTS = 3
RETRY_DELAY_SECONDS = 3
MAX_REQUESTS = 80
DEADLINE_SECONDS = 200
USER_AGENT = "OnO-SmokeTest/2.0"

LEVELS = ("reach", "read", "full")
LOCAL_HOSTS = ("127.0.0.1", "localhost", "::1")
KST = timezone(timedelta(hours=9))

# AuthErrorCase
INVALID_REFRESH_TOKEN = 1001
REFRESH_TOKEN_NOT_FOUND = 1002
REFRESH_TOKEN_EXPIRED = 1006
AUTHENTICATION_FAILED = 1007
ACCESS_TOKEN_EXPIRED = 1005
INVALID_ACCESS_TOKEN = 1009
# FolderErrorCase, PracticeNoteErrorCase
FOLDER_NOT_FOUND = 5001
PRACTICE_NOTE_NOT_FOUND = 6001
# ProblemErrorCase
PROBLEM_NOT_FOUND = 4001

# 오답노트를 등록할 때만 붙인다. 헤더가 없으면 서버가 구버전 앱으로 보고 능력치 포인트(XP)를 적립한다
# (LegacyAccrualPolicy). 미션을 받을 수 있는 버전 기준(ono.mission.mission-capable-version, 기본 4.0.0)이
# 올라가도 계속 신버전으로 읽히도록 크게 둔다. 신버전 요청은 XP 대신 미션 진행도만 올린다.
SMOKE_APP_VERSION = "99.0.0"

WRITE_METHODS = ("POST", "PATCH", "DELETE")
SIGNUP_PATH = "/api/auth/signup/guest"
REFRESH_PATH = "/api/auth/refresh"
PROBLEM_PATH = "/api/problems/v2"
PRESIGNED_PATH = "/api/fileUpload/presigned-urls"

# GET 이지만 서버에 흔적이 남는 요청. 쓰기처럼 full 에서만 보내고 재시도하지 않는다.
# presigned URL 발급은 서명만 하고 S3 객체는 만들지 않지만, 사용자별 하루 200회 카운터(Redis)를 호출마다 1 올린다.
WRITE_LIKE_GETS = (PRESIGNED_PATH,)

# 보낼 수 있는 요청 전부. 경로는 쿼리스트링을 뺀 값과 통째로 맞아야 한다.
ALLOWED_REQUESTS = [
    ("GET", r"/api/problems/problemCount"),
    ("GET", r"/api/folders"),
    ("GET", r"/api/folders/root"),
    ("GET", r"/api/folders/thumbnails/V2"),
    ("GET", r"/api/folders/\d+"),
    ("GET", r"/api/folders/\d+/subfolders/V2"),
    ("GET", r"/api/problems/review-due"),
    ("GET", r"/api/problems/user"),
    ("GET", r"/api/problems/folder/\d+/V2"),
    ("GET", r"/api/practiceNotes/thumbnail"),
    ("GET", r"/api/practiceNotes/thumbnail/V2"),
    ("GET", r"/api/practiceNotes/all"),
    ("GET", r"/api/practiceNotes/\d+"),
    ("GET", r"/api/problem-solves/user/count"),
    ("GET", r"/api/study-room"),
    ("GET", r"/api/tags"),
    ("GET", r"/api/learning-calendar"),
    ("GET", r"/api/learning-reports/summary"),
    ("GET", r"/api/problems/\d+"),
    ("GET", PRESIGNED_PATH),
    ("POST", r"/api/folders"),
    ("PATCH", r"/api/folders"),
    ("DELETE", r"/api/folders"),
    ("POST", r"/api/practiceNotes"),
    ("DELETE", r"/api/practiceNotes"),
    ("POST", PROBLEM_PATH),
    ("DELETE", r"/api/problems"),
    ("POST", REFRESH_PATH),
    ("POST", SIGNUP_PATH),
]


class SmokeAbort(Exception):
    """더 진행하면 안 되는 상황. 남은 확인을 건너뛰고 정리만 한다."""


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

    def data(self):
        body = self.json()
        return body.get("data") if isinstance(body, dict) else None

    def error_code(self):
        body = self.json()
        return body.get("errorCode") if isinstance(body, dict) else None

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


# --- 토큰 ---------------------------------------------------------------------

def _b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def mint_access_token(secret_b64: str, now: int, user_id: str = NONEXISTENT_USER_ID) -> str:
    """JwtTokenizer.createAccessToken 과 같은 모양의 HS256 토큰을 만든다.

    서버는 jwt.accessToken.secret 을 Base64 로 디코딩한 바이트를 HMAC 키로 쓴다.
    """
    payload = {"authority": SMOKE_AUTHORITY, "sub": str(user_id), "iat": now, "exp": now + TOKEN_TTL_SECONDS}
    return _sign(secret_b64, payload)


def mint_refresh_token(secret_b64: str, now: int) -> str:
    """JwtTokenizer.createRefreshToken 과 같은 모양의 토큰을 만든다.

    서버가 발급한 적 없는 토큰이라 refresh_token 테이블에 없고, 서버는 1002 로 끝낸다.
    jti 를 매번 새로 넣어서 우연히라도 저장된 토큰과 같아질 일이 없다.
    """
    payload = {"authority": SMOKE_AUTHORITY, "sub": NONEXISTENT_USER_ID, "iat": now,
               "jti": secrets.token_hex(16), "exp": now + TOKEN_TTL_SECONDS}
    return _sign(secret_b64, payload)


def _sign(secret_b64: str, payload: dict) -> str:
    key = base64.b64decode(secret_b64.strip(), validate=True)
    header = {"alg": "HS256"}
    signing_input = (
        _b64url(json.dumps(header, separators=(",", ":")).encode())
        + "."
        + _b64url(json.dumps(payload, separators=(",", ":")).encode())
    )
    signature = hmac.new(key, signing_input.encode("ascii"), hashlib.sha256).digest()
    return signing_input + "." + _b64url(signature)


def subject_of(token: str) -> str | None:
    """서버가 발급한 토큰에서 sub 를 꺼낸다. 서명은 검증하지 않는다(bootstrap 에서 계정 ID 를 알아낼 때만 쓴다)."""
    parts = token.removeprefix("Bearer ").strip().split(".")
    if len(parts) != 3:
        return None
    try:
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
    except (binascii.Error, ValueError):
        return None
    sub = payload.get("sub") if isinstance(payload, dict) else None
    return str(sub) if sub is not None else None


def _mask(value: str, env: Mapping[str, str]) -> None:
    if env.get("GITHUB_ACTIONS") == "true" and value:
        print(f"::add-mask::{value}")


# --- HTTP ---------------------------------------------------------------------

class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


class Budget:
    """한 번 실행에서 모든 Client 가 함께 쓰는 요청 수와 시간 상한."""

    def __init__(self, limit: int | None = None, deadline_seconds: float | None = None):
        self.limit = MAX_REQUESTS if limit is None else limit
        self.deadline = time.monotonic() + (DEADLINE_SECONDS if deadline_seconds is None else deadline_seconds)
        self.sent = 0

    def spend(self) -> None:
        if self.sent >= self.limit:
            raise SmokeAbort(f"한 번 실행의 요청 상한({self.limit}개)에 닿아 멈췄습니다")
        if time.monotonic() >= self.deadline:
            raise SmokeAbort("실행 시간 상한을 넘어 멈췄습니다. 서버 응답이 매우 느릴 수 있습니다")
        self.sent += 1


class Client:
    """이 스크립트가 서버에 요청을 보내는 유일한 통로."""

    def __init__(self, base_url: str, sleep: Callable[[float], None], budget: Budget, token: str | None = None,
                 allow_writes: bool = False, allow_signup: bool = False):
        self.base_url = base_url.rstrip("/")
        self.sleep = sleep
        self.budget = budget
        self.token = token
        self.allow_writes = allow_writes
        self.allow_signup = allow_signup

    def request(self, method: str, path: str, body=None, query: Mapping[str, object] | None = None,
                auth: bool = True, extra_headers: Mapping[str, str] | None = None) -> Response:
        method = method.upper()
        if not any(m == method and re.fullmatch(p, path) for m, p in ALLOWED_REQUESTS):
            raise SmokeAbort(f"허용 목록에 없는 요청이라 보내지 않았습니다: {method} {path}")
        if path == SIGNUP_PATH:
            if not self.allow_signup:
                raise SmokeAbort("게스트 가입은 bootstrap 에서만 보낼 수 있습니다")
        elif path == REFRESH_PATH:
            # 저장된 적 없는 토큰만 보내므로 서버에 쓰기가 없다. 그래서 read 에서도 허용한다.
            # 서버에 저장된 진짜 refresh token 을 보내면 토큰이 회전(쓰기)되므로, 없는 사용자(sub 0)로 만든 토큰만 통과시킨다.
            token = body.get("refreshToken") if isinstance(body, dict) else None
            if auth or not isinstance(token, str) or subject_of(token) != NONEXISTENT_USER_ID:
                raise SmokeAbort("토큰 갱신은 없는 사용자로 만든 확인용 토큰만 보낼 수 있습니다")
        elif (method in WRITE_METHODS or path in WRITE_LIKE_GETS) and not self.allow_writes:
            raise SmokeAbort(f"쓰기 요청은 full 단계에서만 보낼 수 있습니다: {method} {path}")

        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json", **(extra_headers or {})}
        if auth and self.token:
            headers["Authorization"] = self.token
        data = None
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"

        res = self._send(method, url, headers, data)
        attempts = 1
        # 쓰기는 보낸 요청이 서버에 반영됐는지 알 수 없어서 재시도하지 않는다. 흔적이 남는 GET 도 같다.
        # 토큰 갱신 확인은 서버에 쓰기가 없어 재시도해도 된다. 막 뜬 새 컨테이너에 두 번째로 가는 요청이라 느릴 수 있다.
        retryable = (method == "GET" and path not in WRITE_LIKE_GETS) or path == REFRESH_PATH
        while retryable and _should_retry(res) and attempts < MAX_ATTEMPTS:
            self.sleep(RETRY_DELAY_SECONDS)
            res = self._send(method, url, headers, data)
            attempts += 1
        res.attempts = attempts
        return res

    def _send(self, method: str, url: str, headers: Mapping[str, str], data: bytes | None) -> Response:
        self.budget.spend()
        request = urllib.request.Request(url, data=data, method=method, headers=dict(headers))
        started = time.monotonic()
        try:
            with _OPENER.open(request, timeout=REQUEST_TIMEOUT_SECONDS) as res:
                return Response(res.status, dict(res.headers), res.read(), elapsed_ms=_ms(started))
        except urllib.error.HTTPError as e:
            with e:
                return Response(e.code, dict(e.headers or {}), e.read(), elapsed_ms=_ms(started))
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            return Response(None, error=str(getattr(e, "reason", e)), elapsed_ms=_ms(started))


def _ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)


def _should_retry(res: Response) -> bool:
    return res.status is None or res.status == 429 or res.status >= 500


def _lower_headers(res: Response) -> dict[str, str]:
    return {k.lower(): v for k, v in res.headers.items()}


def describe_unexpected(res: Response) -> str:
    if res.status is None:
        return f"서버에 닿지 않았습니다 ({res.error}, {res.attempts}번 시도)"
    headers = _lower_headers(res)
    server = headers.get("server", "").lower()
    if 300 <= res.status < 400:
        return (f"리다이렉트 응답입니다 (HTTP {res.status}, Location: {headers.get('location', '-')}). "
                "따라가지 않았습니다. 점검 모드나 nginx 설정을 확인해야 합니다")
    if "cf-mitigated" in headers or (res.status == 403 and "cloudflare" in server and res.json() is None):
        return f"Cloudflare 가 요청을 막았습니다 (HTTP {res.status}). 앱까지 가지 않아서 서버 상태는 알 수 없습니다"
    if res.status >= 500 and "cloudflare" in server and res.json() is None:
        # 원 서버 응답이면 nginx 나 앱이 만든 본문이 온다. Cloudflare 가 직접 만든 5xx 는
        # 원 서버(맥미니 nginx)까지 닿지 못했다는 뜻이라 앱 오류와 구분해서 알린다.
        return (f"Cloudflare 가 원 서버에 닿지 못했습니다 (HTTP {res.status}, {res.attempts}번 시도). "
                "서버나 nginx 가 꺼져 있거나 네트워크가 끊겼을 수 있습니다")
    if res.status >= 500:
        return f"앱이 정상 응답하지 않습니다 (HTTP {res.status}, {res.attempts}번 시도): {res.snippet()}"
    if res.json() is None:
        return f"JSON 이 아닌 응답입니다 (HTTP {res.status}). nginx 오류 페이지나 점검 페이지일 수 있습니다: {res.snippet()}"
    if res.status == 401:
        return describe_token_rejection(res)
    return f"예상과 다른 응답입니다 (HTTP {res.status}): {res.snippet()}"


def describe_token_rejection(res: Response) -> str:
    # 서버 버전에 따라 같은 원인에도 코드가 다르게 나와서 가능한 원인을 같이 적는다.
    # main(2026-07-01)은 서명이 틀려도 1005 를 주고, develop 은 1009 를 준다.
    # 1007 은 블랙리스트 조회(Redis) 실패처럼 토큰과 무관한 예외에서도 나온다.
    causes = {
        INVALID_ACCESS_TOKEN: "SMOKE_ACCESS_TOKEN_SECRET 이 서버의 JWT 서명 키와 다를 수 있습니다",
        ACCESS_TOKEN_EXPIRED: "서명 키가 다르거나(구버전 서버는 이때도 1005 를 줍니다) 러너와 서버의 시계가 어긋났을 수 있습니다",
        AUTHENTICATION_FAILED: "서명 키가 다르거나 서버의 Redis(토큰 블랙리스트 조회)에 문제가 있을 수 있습니다",
    }
    code = res.error_code()
    return f"토큰이 거절됐습니다 (errorCode {code}). {causes.get(code, res.snippet())}"


# --- 판정 도우미 -----------------------------------------------------------------

def is_count(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def is_cursor_page(value) -> bool:
    return isinstance(value, dict) and isinstance(value.get("content"), list) and "hasNext" in value


def has_keys(*keys: str) -> Callable[[object], bool]:
    return lambda value: isinstance(value, dict) and all(k in value for k in keys)


def is_list(value) -> bool:
    return isinstance(value, list)


def is_dict(value) -> bool:
    return isinstance(value, dict)


# --- 확인 -----------------------------------------------------------------------

class SmokeRun:
    def __init__(self, env: Mapping[str, str], sleep: Callable[[float], None], clock: Callable[[], float]):
        self.env = env
        self.sleep = sleep
        self.clock = clock
        self.results: list[CheckResult] = []
        self.budget = Budget()
        self.base_url = env.get("SMOKE_BASE_URL", "").strip()
        self.level = env.get("SMOKE_LEVEL", "").strip() or "read"
        self.secret = env.get("SMOKE_ACCESS_TOKEN_SECRET", "").strip()
        self.refresh_secret = env.get("SMOKE_REFRESH_TOKEN_SECRET", "").strip()
        self.user_id = env.get("SMOKE_USER_ID", "").strip()
        stamp = datetime.fromtimestamp(clock(), timezone.utc).strftime("%Y%m%dT%H%M%S")
        self.run_tag = f"{RUN_PREFIX}{stamp}_{secrets.token_hex(2)}"

    # 결과 기록
    def ok(self, name: str, detail: str, res: Response | None = None) -> None:
        self.results.append(CheckResult(name, True, detail, res.elapsed_ms if res else 0))

    def fail(self, name: str, detail: str, res: Response | None = None) -> None:
        self.results.append(CheckResult(name, False, detail, res.elapsed_ms if res else 0))

    def skip(self, name: str, detail: str, passed: bool = True) -> None:
        self.results.append(CheckResult(name, passed, detail, skipped=True))

    def expect(self, name: str, res: Response, status: int,
               validate: Callable[[object], bool] | None = None) -> bool:
        if res.status == status and (validate is None or validate(res.data())):
            self.ok(name, f"HTTP {status}", res)
            return True
        if res.status == status:
            self.fail(name, f"응답 모양이 예상과 다릅니다 (HTTP {status}): {res.snippet()}", res)
        else:
            self.fail(name, describe_unexpected(res), res)
        return False

    # 전체 흐름
    def validate_config(self) -> str | None:
        if not self.base_url.startswith(("https://", "http://")):
            return "SMOKE_BASE_URL 이 없거나 http(s):// 로 시작하지 않습니다"
        if self.level not in LEVELS:
            return f"SMOKE_LEVEL 은 {', '.join(LEVELS)} 중 하나여야 합니다: {self.level}"
        host = urllib.parse.urlsplit(self.base_url).hostname or ""
        if (self.secret or self.refresh_secret) and self.base_url.startswith("http://") and host not in LOCAL_HOSTS:
            return "토큰을 평문으로 보내지 않도록 로컬 주소가 아니면 https:// 만 받습니다"
        if self.user_id and not re.fullmatch(r"[0-9]+", self.user_id):
            return "SMOKE_USER_ID 는 숫자여야 합니다"
        if self.secret:
            try:
                base64.b64decode(self.secret, validate=True)
            except (binascii.Error, ValueError):
                return "SMOKE_ACCESS_TOKEN_SECRET 이 Base64 값이 아닙니다"
        if self.refresh_secret:
            try:
                base64.b64decode(self.refresh_secret, validate=True)
            except (binascii.Error, ValueError):
                return "SMOKE_REFRESH_TOKEN_SECRET 이 Base64 값이 아닙니다"
        return None

    def execute(self) -> None:
        if not self.check_reachable(Client(self.base_url, self.sleep, self.budget)):
            self.skip("나머지 확인", "앱까지 닿지 않아서 요청을 더 보내지 않았습니다", passed=False)
            return
        if self.level == "reach":
            return
        if self.refresh_secret:
            self.check_refresh(Client(self.base_url, self.sleep, self.budget))
        else:
            self.skip("인증: 토큰 갱신 경로", "SMOKE_REFRESH_TOKEN_SECRET 이 없어 건너뛰었습니다")
        if not self.secret:
            self.skip("인증과 조회", "SMOKE_ACCESS_TOKEN_SECRET 이 없어 건너뛰었습니다")
            return
        if not self.user_id:
            self.check_probe_with_nonexistent_user()
            self.skip("스모크 계정 확인", "SMOKE_USER_ID 가 없어 조회 세트와 쓰기 흐름을 건너뛰었습니다")
            return

        token = "Bearer " + mint_access_token(self.secret, int(self.clock()), self.user_id)
        _mask(token, self.env)
        client = Client(self.base_url, self.sleep, self.budget, token=token, allow_writes=(self.level == "full"))
        root = self.check_smoke_account(client)
        if root is None:
            return
        self.check_reads(client, root)
        if self.level != "full":
            return
        if any(not r.passed for r in self.results):
            self.skip("쓰기 흐름", "조회가 실패해서 쓰기를 시작하지 않았습니다")
            return
        self.check_writes(client, root)

    def check_reachable(self, client: Client) -> bool:
        name = "앱까지 닿는가 (토큰 없이 401)"
        res = client.request("GET", PROBE_PATH, auth=False)
        if res.status == 401 and res.error_code() == AUTHENTICATION_FAILED:
            self.ok(name, f"HTTP 401, errorCode {AUTHENTICATION_FAILED}", res)
            return True
        if res.status == 200:
            self.fail(name, "토큰 없이 200 이 나왔습니다. 인증 설정이 풀렸는지 확인이 필요합니다", res)
        else:
            self.fail(name, describe_unexpected(res), res)
        return False

    def check_refresh(self, client: Client) -> None:
        name = "인증: 토큰 갱신 경로 (없는 refresh token 으로 1002)"
        token = mint_refresh_token(self.refresh_secret, int(self.clock()))
        _mask(token, self.env)
        res = client.request("POST", REFRESH_PATH, {"refreshToken": token}, auth=False)
        code = res.error_code()
        if res.status == 401 and code == REFRESH_TOKEN_NOT_FOUND:
            self.ok(name, f"HTTP 401, errorCode {REFRESH_TOKEN_NOT_FOUND}", res)
        elif code == INVALID_REFRESH_TOKEN:
            self.fail(name, f"refresh token 을 거절했습니다 (errorCode {code}). SMOKE_REFRESH_TOKEN_SECRET 이 서버의 "
                            "refresh 서명 키(APPLICATION_PROD 의 jwt.refreshToken.secret)와 다를 수 있습니다. "
                            "서버 키가 바뀐 것이라면 앱 사용자의 기존 refresh token 이 전부 거절됩니다", res)
        elif code == REFRESH_TOKEN_EXPIRED:
            self.fail(name, f"방금 만든 토큰을 만료로 봤습니다 (errorCode {code}). 러너와 서버의 시계가 어긋났을 수 있습니다", res)
        elif res.status == 200:
            self.fail(name, "저장된 적 없는 refresh token 으로 갱신에 성공했습니다. 토큰 저장소 확인이 빠졌을 수 있습니다", res)
        else:
            self.fail(name, describe_unexpected(res), res)

    def check_probe_with_nonexistent_user(self) -> None:
        token = "Bearer " + mint_access_token(self.secret, int(self.clock()))
        _mask(token, self.env)
        client = Client(self.base_url, self.sleep, self.budget, token=token)
        self.expect("인증과 DB 조회가 되는가 (없는 사용자 토큰으로 count)", client.request("GET", PROBE_PATH), 200, is_count)

    def check_smoke_account(self, client: Client) -> dict | None:
        name = "스모크 계정 확인 (루트 폴더의 표식 폴더)"
        res = client.request("GET", "/api/folders/root")
        data = res.data()
        if res.status != 200 or not has_keys("folderId", "subFolderList")(data):
            # 계정이 스모크 계정인지 모르는 상태라, 200 응답 본문은 로그와 알림에 싣지 않는다(공개 레포).
            detail = "루트 폴더 응답 모양이 예상과 다릅니다" if res.status == 200 else describe_unexpected(res)
            self.fail(name, detail, res)
            self.skip("나머지 확인", "스모크 계정을 확인하지 못해 요청을 더 보내지 않았습니다", passed=False)
            return None
        if "userId" in data and str(data["userId"]) != self.user_id:
            self.fail(name, "루트 폴더의 주인이 SMOKE_USER_ID 와 다릅니다. 요청을 더 보내지 않았습니다", res)
            self.skip("나머지 확인", "다른 계정의 데이터일 수 있어 멈췄습니다", passed=False)
            return None
        names = [f.get("folderName") for f in data["subFolderList"] if isinstance(f, dict)]
        if MARKER_FOLDER_NAME not in names:
            self.fail(name, f"루트 폴더에 {MARKER_FOLDER_NAME} 폴더가 없습니다. "
                            "SMOKE_USER_ID 가 스모크 계정이 아닐 수 있어 멈췄습니다", res)
            self.skip("나머지 확인", "실사용자 계정일 수 있어 요청을 더 보내지 않았습니다", passed=False)
            return None
        self.ok(name, "HTTP 200, 표식 폴더 있음", res)
        return data

    def check_reads(self, client: Client, root: dict) -> None:
        root_id = root["folderId"]
        now = datetime.fromtimestamp(self.clock(), KST)
        reads = [
            ("폴더 목록", "/api/folders", None, is_list),
            ("폴더 썸네일", "/api/folders/thumbnails/V2", None, is_cursor_page),
            ("폴더 단건", f"/api/folders/{root_id}", None, has_keys("folderId", "folderName")),
            ("하위 폴더", f"/api/folders/{root_id}/subfolders/V2", None, is_cursor_page),
            ("오답노트 수", PROBE_PATH, None, is_count),
            ("복습할 문제", "/api/problems/review-due", None, has_keys("dueCount", "overdueCount", "problems")),
            ("내 오답노트", "/api/problems/user", None, is_list),
            ("폴더 안 오답노트", f"/api/problems/folder/{root_id}/V2", None, is_cursor_page),
            ("복습노트 썸네일", "/api/practiceNotes/thumbnail/V2", None, is_cursor_page),
            ("복습노트 전체", "/api/practiceNotes/all", None, is_list),
            ("풀이 기록 수", "/api/problem-solves/user/count", None, is_count),
            ("내 스터디룸", "/api/study-room", None, is_list),
            ("태그", "/api/tags", None, is_list),
            ("학습 캘린더", "/api/learning-calendar", {"year": now.year, "month": now.month},
             has_keys("year", "month", "records")),
            ("학습 리포트 요약", "/api/learning-reports/summary", None, is_dict),
        ]
        for label, path, query, validate in reads:
            self.expect(f"조회: {label}", client.request("GET", path, query=query), 200, validate)

    # 쓰기 흐름 -------------------------------------------------------------------

    def check_writes(self, client: Client, root: dict) -> None:
        created_folders: list[int] = []
        created_notes: list[int] = []
        created_problems: list[int] = []
        try:
            self.clean_leftovers(client, root)
            self.folder_flow(client, root["folderId"], created_folders)
            self.practice_note_flow(client, created_notes)
            self.problem_flow(client, root["folderId"], created_problems)
        except SmokeAbort as e:
            self.fail("쓰기 흐름 중단", str(e))
        finally:
            self.cleanup(client, created_folders, created_notes, created_problems)

    def clean_leftovers(self, client: Client, root: dict) -> None:
        """지난 실행이 중간에 끊겨 남긴 스모크 폴더와 복습노트, 오답노트를 먼저 지운다."""
        name = "지난 실행 흔적 정리"
        folder_ids = [f["folderId"] for f in root["subFolderList"]
                      if isinstance(f, dict) and str(f.get("folderName", "")).startswith(RUN_PREFIX)]
        res = client.request("GET", "/api/practiceNotes/thumbnail")
        if res.status != 200 or not is_list(res.data()):
            self.fail(name, describe_unexpected(res) if res.status != 200 else "복습노트 목록 모양이 예상과 다릅니다", res)
            raise SmokeAbort("흔적 목록을 읽지 못해 쓰기 흐름을 시작하지 않았습니다")
        note_ids = [n["practiceNoteId"] for n in res.data()
                    if isinstance(n, dict) and str(n.get("practiceTitle", "")).startswith(RUN_PREFIX)]
        res = client.request("GET", "/api/problems/user")
        if res.status != 200 or not is_list(res.data()):
            self.fail(name, describe_unexpected(res) if res.status != 200 else "오답노트 목록 모양이 예상과 다릅니다", res)
            raise SmokeAbort("흔적 목록을 읽지 못해 쓰기 흐름을 시작하지 않았습니다")
        problem_ids = [p["problemId"] for p in res.data()
                       if isinstance(p, dict) and str(p.get("memo") or "").startswith(RUN_PREFIX)]
        if not folder_ids and not note_ids and not problem_ids:
            self.ok(name, "남은 흔적 없음", res)
            return
        failed = []
        if folder_ids and self.delete_folders(client, folder_ids).status != 200:
            failed.append(f"폴더 {folder_ids}")
        if note_ids and self.delete_notes(client, note_ids).status != 200:
            failed.append(f"복습노트 {note_ids}")
        if problem_ids and self.delete_problems(client, problem_ids).status != 200:
            failed.append(f"오답노트 {problem_ids}")
        if failed:
            self.fail(name, f"지우지 못했습니다: {', '.join(failed)}")
        else:
            self.ok(name, f"폴더 {len(folder_ids)}개, 복습노트 {len(note_ids)}개, 오답노트 {len(problem_ids)}개를 지웠습니다")

    def folder_flow(self, client: Client, root_id: int, created: list[int]) -> None:
        folder_name = self.run_tag
        res = client.request("POST", "/api/folders", {"folderName": folder_name, "parentFolderId": root_id})
        if not self.expect("쓰기: 폴더 만들기", res, 200, is_count):
            raise SmokeAbort("폴더를 만들지 못해 쓰기 흐름을 멈췄습니다")
        folder_id = res.data()
        created.append(folder_id)

        def created_under_root(v) -> bool:
            parent = v.get("parentFolder") if isinstance(v, dict) else None
            return (isinstance(v, dict) and v.get("folderName") == folder_name
                    and isinstance(parent, dict) and parent.get("folderId") == root_id)

        self.expect("쓰기: 만든 폴더 조회", client.request("GET", f"/api/folders/{folder_id}"), 200, created_under_root)

        renamed = folder_name + "_r"
        res = client.request("PATCH", "/api/folders", {"folderId": folder_id, "folderName": renamed})
        if self.expect("쓰기: 폴더 이름 바꾸기", res, 200):
            self.expect("쓰기: 바뀐 이름 조회", client.request("GET", f"/api/folders/{folder_id}"), 200,
                        lambda v: isinstance(v, dict) and v.get("folderName") == renamed)

        if self.expect("쓰기: 폴더 지우기", self.delete_folders(client, [folder_id]), 200):
            res = client.request("GET", f"/api/folders/{folder_id}")
            if res.status == 404 and res.error_code() == FOLDER_NOT_FOUND:
                self.ok("쓰기: 지운 폴더가 안 보이는가", f"HTTP 404, errorCode {FOLDER_NOT_FOUND}", res)
                created.remove(folder_id)
            else:
                self.fail("쓰기: 지운 폴더가 안 보이는가",
                          f"삭제 후에도 조회됩니다 (HTTP {res.status}). 삭제가 반영되지 않았을 수 있습니다", res)

    def practice_note_flow(self, client: Client, created: list[int]) -> None:
        title = self.run_tag
        # practiceNotification 을 보내지 않는다. 보내면 Quartz 알림 작업이 등록되고,
        # 운영 main 버전은 복습노트를 지워도 그 작업을 지우지 않는다.
        res = client.request("POST", "/api/practiceNotes", {"practiceTitle": title, "problemIdList": []})
        if not self.expect("쓰기: 복습노트 만들기", res, 201, is_count):
            raise SmokeAbort("복습노트를 만들지 못해 쓰기 흐름을 멈췄습니다")
        note_id = res.data()
        created.append(note_id)

        self.expect("쓰기: 만든 복습노트 조회", client.request("GET", f"/api/practiceNotes/{note_id}"), 200,
                    lambda v: isinstance(v, dict) and v.get("practiceTitle") == title
                    and v.get("practiceNotification") is None)

        if self.expect("쓰기: 복습노트 지우기", self.delete_notes(client, [note_id]), 200):
            res = client.request("GET", f"/api/practiceNotes/{note_id}")
            if res.status == 404 and res.error_code() == PRACTICE_NOTE_NOT_FOUND:
                self.ok("쓰기: 지운 복습노트가 안 보이는가", f"HTTP 404, errorCode {PRACTICE_NOTE_NOT_FOUND}", res)
                created.remove(note_id)
            else:
                self.fail("쓰기: 지운 복습노트가 안 보이는가",
                          f"삭제 후에도 조회됩니다 (HTTP {res.status}). 삭제가 반영되지 않았을 수 있습니다", res)

    def problem_flow(self, client: Client, root_id: int, created: list[int]) -> None:
        """오답노트를 이미지 없이 등록하고 조회하고 지운다.

        지워도 되돌려지지 않는 것이 있다. 하루 3건까지의 mission_log 행과 미션 진행도, CANCELED 로 바뀐 복습
        리마인더 행, soft delete 된 problem 행이 스모크 계정에 남는다. XP 는 SMOKE_APP_VERSION 헤더로 막는다.
        """
        res = client.request("GET", PRESIGNED_PATH, query={"count": 1, "contentType": "image/png"})
        urls = res.data()
        # 서명된 URL 은 10분 동안 버킷에 올릴 수 있는 값이라, 모양이 틀려도 본문을 로그에 싣지 않는다.
        if (res.status == 200 and isinstance(urls, list) and len(urls) == 1 and isinstance(urls[0], dict)
                and str(urls[0].get("presignedUrl", "")).startswith("https://") and urls[0].get("fileUrl")):
            self.ok("쓰기: 이미지 업로드 URL 발급", "HTTP 200, URL 1개", res)
        elif res.status == 200:
            self.fail("쓰기: 이미지 업로드 URL 발급", "응답 모양이 예상과 다릅니다 (HTTP 200)", res)
        else:
            self.fail("쓰기: 이미지 업로드 URL 발급", describe_unexpected(res), res)

        memo = self.run_tag
        solved_at = datetime.fromtimestamp(self.clock(), KST).replace(tzinfo=None, microsecond=0).isoformat()
        body = {"memo": memo, "reference": None, "folderId": root_id, "solvedAt": solved_at,
                "problemImageUrls": [], "answerImageUrls": [], "tagIds": []}
        res = client.request("POST", PROBLEM_PATH, body, extra_headers={"X-App-Version": SMOKE_APP_VERSION})
        if not self.expect("쓰기: 오답노트 등록", res, 200, is_count):
            raise SmokeAbort("오답노트를 등록하지 못해 쓰기 흐름을 멈췄습니다")
        problem_id = res.data()
        created.append(problem_id)

        self.expect("쓰기: 등록한 오답노트 조회", client.request("GET", f"/api/problems/{problem_id}"), 200,
                    lambda v: isinstance(v, dict) and v.get("memo") == memo and v.get("folderId") == root_id)

        if self.expect("쓰기: 오답노트 지우기", self.delete_problems(client, [problem_id]), 200):
            res = client.request("GET", f"/api/problems/{problem_id}")
            if res.status == 404 and res.error_code() == PROBLEM_NOT_FOUND:
                self.ok("쓰기: 지운 오답노트가 안 보이는가", f"HTTP 404, errorCode {PROBLEM_NOT_FOUND}", res)
                created.remove(problem_id)
            else:
                self.fail("쓰기: 지운 오답노트가 안 보이는가",
                          f"삭제 후에도 조회됩니다 (HTTP {res.status}). 삭제가 반영되지 않았을 수 있습니다", res)

    @staticmethod
    def delete_folders(client: Client, folder_ids: list[int]) -> Response:
        return client.request("DELETE", "/api/folders", {"deleteFolderIdList": folder_ids})

    @staticmethod
    def delete_notes(client: Client, note_ids: list[int]) -> Response:
        return client.request("DELETE", "/api/practiceNotes", {"deletePracticeIdList": note_ids})

    @staticmethod
    def delete_problems(client: Client, problem_ids: list[int]) -> Response:
        return client.request("DELETE", "/api/problems", {"deleteProblemIdList": problem_ids})

    def cleanup(self, client: Client, folders: list[int], notes: list[int], problems: list[int]) -> None:
        """흐름이 중간에 멈춰 남은 것을 지운다. 여기서도 못 지우면 다음 실행의 흔적 정리가 지운다."""
        if not folders and not notes and not problems:
            return
        leftovers = []
        try:
            if folders and self.delete_folders(client, folders).status not in (200, 404):
                leftovers.append(f"폴더 {folders}")
            if notes and self.delete_notes(client, notes).status not in (200, 404):
                leftovers.append(f"복습노트 {notes}")
            if problems and self.delete_problems(client, problems).status not in (200, 404):
                leftovers.append(f"오답노트 {problems}")
        except SmokeAbort as e:
            leftovers.append(str(e))
        if leftovers:
            self.fail("마무리 정리", f"지우지 못했습니다: {', '.join(leftovers)}. 다음 실행이 다시 지웁니다")
        else:
            self.ok("마무리 정리", "중간에 남은 것을 지웠습니다")


# --- bootstrap ------------------------------------------------------------------

def bootstrap(env: Mapping[str, str], sleep: Callable[[float], None] = time.sleep) -> int:
    """스모크 게스트 계정을 만들고 표식 폴더를 단다. 가입할 때 Discord 알림이 한 번 간다."""
    base_url = env.get("SMOKE_BASE_URL", "").strip()
    if not base_url.startswith(("https://", "http://")):
        print("SMOKE_BASE_URL 이 없거나 http(s):// 로 시작하지 않습니다", file=sys.stderr)
        return 2
    if env.get("SMOKE_USER_ID", "").strip():
        print("SMOKE_USER_ID 가 이미 있습니다. 계정을 또 만들지 않도록 멈췄습니다", file=sys.stderr)
        return 2

    client = Client(base_url, sleep, Budget(limit=10), allow_signup=True)
    try:
        res = client.request("POST", SIGNUP_PATH, auth=False)
        data = res.data()
        token = data.get("accessToken") if isinstance(data, dict) else None
        if res.status != 200 or not token:
            print(f"게스트 가입 실패: {describe_unexpected(res)}", file=sys.stderr)
            return 1
        _mask(token, env)
        if isinstance(data, dict) and data.get("refreshToken"):
            _mask(data["refreshToken"], env)
        user_id = subject_of(token)
        if not user_id:
            print("발급된 토큰에서 계정 ID 를 읽지 못했습니다", file=sys.stderr)
            return 1

        client.token = token if token.startswith("Bearer ") else "Bearer " + token
        client.allow_writes = True
        res = client.request("GET", "/api/folders/root")
        root = res.data()
        if res.status != 200 or not has_keys("folderId", "subFolderList")(root):
            print(f"루트 폴더 조회 실패: {describe_unexpected(res)}", file=sys.stderr)
            return 1
        res = client.request("POST", "/api/folders", {"folderName": MARKER_FOLDER_NAME, "parentFolderId": root["folderId"]})
        if res.status != 200:
            print(f"표식 폴더 만들기 실패: {describe_unexpected(res)}", file=sys.stderr)
            return 1
    except SmokeAbort as e:
        print(str(e), file=sys.stderr)
        return 1

    print(f"스모크 계정을 만들었습니다. SMOKE_USER_ID = {user_id}")
    summary = env.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"## 스모크 계정 만들기\n\n- 대상: `{base_url}`\n- 계정 ID: `{user_id}`\n"
                    "- 이 값을 저장소 Secrets 의 `SMOKE_PROD_USER_ID` (dev 는 `SMOKE_DEV_USER_ID`) 에 넣으세요.\n")
    return 0


# --- 실행 -----------------------------------------------------------------------

def run(env: Mapping[str, str], sleep: Callable[[float], None] = time.sleep,
        clock: Callable[[], float] = time.time) -> int:
    smoke = SmokeRun(env, sleep, clock)
    problem = smoke.validate_config()
    if problem:
        print(problem, file=sys.stderr)
        return 2

    print(f"대상: {smoke.base_url} (단계: {smoke.level})")
    try:
        smoke.execute()
    except SmokeAbort as e:
        smoke.fail("실행 중단", str(e))

    failed = [r for r in smoke.results if not r.passed]
    for r in smoke.results:
        mark = "건너뜀" if r.skipped else ("통과" if r.passed else "실패")
        elapsed = f" ({r.elapsed_ms}ms)" if r.elapsed_ms else ""
        print(f"[{mark}] {r.name}{elapsed}: {r.detail}")
    _write_step_summary(env, smoke, failed)
    _write_outputs(env, smoke, failed)

    print(f"보낸 요청: {smoke.budget.sent}개 (상한 {smoke.budget.limit}개)")
    print("스모크 테스트 실패" if failed else "스모크 테스트 통과")
    return 1 if failed else 0


def _write_step_summary(env: Mapping[str, str], smoke: SmokeRun, failed: list[CheckResult]) -> None:
    path = env.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = [
        "## 스모크 테스트",
        "",
        f"- 대상: `{smoke.base_url}`, 단계: `{smoke.level}`, 보낸 요청: {smoke.budget.sent}개",
        f"- 결과: {'❌ 실패 ' + str(len(failed)) + '건' if failed else '✅ 통과'}",
        "",
        "| 확인 | 결과 | 응답 시간 | 내용 |",
        "|---|---|---:|---|",
    ]
    for r in smoke.results:
        mark = "건너뜀" if r.skipped else ("✅" if r.passed else "❌")
        elapsed = f"{r.elapsed_ms}ms" if r.elapsed_ms else "-"
        lines.append(f"| {r.name} | {mark} | {elapsed} | {r.detail.replace('|', '/')} |")
    with open(path, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def _write_outputs(env: Mapping[str, str], smoke: SmokeRun, failed: list[CheckResult]) -> None:
    path = env.get("GITHUB_OUTPUT")
    if not path:
        return
    first = f"{failed[0].name}: {failed[0].detail}" if failed else ""
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"failed={len(failed)}\n")
        f.write(f"first_failure={first.replace(chr(10), ' ')[:300]}\n")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "bootstrap":
        sys.exit(bootstrap(os.environ))
    if len(sys.argv) > 1:
        print(f"알 수 없는 명령: {sys.argv[1]}", file=sys.stderr)
        sys.exit(2)
    sys.exit(run(os.environ))
