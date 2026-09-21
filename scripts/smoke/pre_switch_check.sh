#!/usr/bin/env bash
# nginx 를 새 컨테이너로 넘기기 직전에 그 컨테이너를 직접 확인한다. ci-prod.yml 배포 job(맥미니 러너)이 부른다.
#
#   pre_switch_check.sh <컨테이너 이름> <호스트 포트>
#
# 1. 컨테이너 안 actuator health. 구성요소(db, redis, rabbit) 중 하나라도 DOWN 이면 503 이라 wget 이 실패한다.
#    컨테이너 healthcheck 도 같은 주소를 보지만 30초 간격에 3번 실패해야 unhealthy 가 되어서, 전환 직전에 한 번 더 본다.
#    응답 본문에는 DB 버전과 디스크 용량 같은 값이 들어 있고 Actions 로그는 공개라, 구성요소 이름과 상태만 찍는다.
# 2. 스모크 테스트 read 를 http://127.0.0.1:<포트> 로. Cloudflare 와 nginx 를 거치지 않고 새 버전 앱만 본다.
#    쓰기가 있는 full 은 전환 뒤 smoke-test.yml 이 돌린다. 전환 전에는 이전 버전이 같은 DB 로 사용자를 받고 있다.
#
# 종료 코드 0 이면 전환해도 된다. 0 이 아니면 호출한 쪽이 새 컨테이너를 내리고 전환하지 않는다.
# python3 가 없거나 너무 오래됐으면 스모크 테스트만 건너뛰고 경고를 남긴다. 이 스텝이 생기기 전과 같은 상태라
# 배포를 막지 않는다.
#
# 필요한 환경 변수: SMOKE_ACCESS_TOKEN_SECRET, SMOKE_REFRESH_TOKEN_SECRET, SMOKE_USER_ID (없으면 할 수 있는 만큼만 한다)
# 시험할 때는 DOCKER 로 docker 대신 쓸 명령을 넣을 수 있다.

set -uo pipefail

container="${1:?컨테이너 이름이 필요합니다}"
port="${2:?호스트 포트가 필요합니다}"
docker_cmd="${DOCKER:-docker}"
here="$(cd "$(dirname "$0")" && pwd)"

echo "=== 전환 전 확인: $container (포트 $port) ==="

python_ok=false
if command -v python3 >/dev/null 2>&1 && python3 -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>/dev/null; then
  python_ok=true
fi

if ! health="$($docker_cmd exec "$container" wget -qO- --tries=1 --timeout=10 http://localhost:8081/actuator/health)"; then
  echo "[실패] actuator health 가 200 이 아닙니다. db, redis, rabbit 중 하나가 DOWN 이거나 앱이 응답하지 않습니다"
  exit 1
fi
if [ "$python_ok" = true ]; then
  HEALTH_JSON="$health" python3 - <<'PY'
import json, os
try:
    body = json.loads(os.environ["HEALTH_JSON"])
except ValueError:
    body = None
components = body.get("components") if isinstance(body, dict) else None
if body is None:
    print("[통과] actuator health: 200 (본문이 JSON 이 아니라 구성요소는 읽지 못했습니다)")
elif not isinstance(components, dict):
    print("[통과] actuator health: 200 (show-details 가 꺼져 있어 구성요소는 보이지 않습니다)")
else:
    parts = ", ".join(f"{name} {c.get('status') if isinstance(c, dict) else '?'}" for name, c in sorted(components.items()))
    print(f"[통과] actuator health: 200 ({parts})")
PY
else
  echo "[통과] actuator health: 200"
fi

if [ "$python_ok" != true ]; then
  echo "::warning::python3 3.9 이상이 없어 전환 전 스모크 테스트를 건너뛰었습니다. 러너에 python3 를 설치해야 합니다"
  exit 0
fi

SMOKE_BASE_URL="http://127.0.0.1:$port" SMOKE_LEVEL=read python3 "$here/smoke_test.py"
