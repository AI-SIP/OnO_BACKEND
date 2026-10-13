#!/usr/bin/env bash
# 배포 범위에서 API 계약과 운영 동작에 닿는 변경만 뽑는다. 읽기 전용이다(git fetch 외에 아무것도 바꾸지 않는다).
#
# 사용법: bash .claude/skills/release-check/extract-changes.sh [범위]
#   범위 기본값은 origin/main..origin/develop (지금 운영 = main, 배포 후보 = develop)
set -euo pipefail

range="${1:-origin/main..origin/develop}"
cd "$(git rev-parse --show-toplevel)"
git fetch origin --quiet

java='src/main/java'
section() { printf '\n## %s\n' "$1"; }

section "범위"
echo "$range"
for ref in ${range%%..*} ${range##*..}; do
  printf '%s  %s\n' "$ref" "$(git log -1 --format='%h %ad %s' --date=short "$ref")"
done
printf '커밋(머지 제외) %s개\n' "$(git log --oneline --no-merges "$range" | wc -l | tr -d ' ')"
git diff --shortstat "$range"
if [ -z "$(git diff --name-only "$range")" ]; then
  echo "변경 없음. 배포할 것이 없습니다."
  exit 0
fi

section "엔드포인트 매핑 변경 (Controller)"
git diff -U0 "$range" -- "$java/*/controller/*.java" \
  | grep -E '^(\+\+\+ b/|[+-][^+-].*@(Get|Post|Put|Patch|Delete|Request)Mapping|[+-][^+-].*@(RequestParam|PathVariable|RequestHeader|RequestPart)\b)' \
  | grep -B1 -E '^[+-][^+-]' | grep -v '^--$' || echo "(없음)"

section "요청/응답 DTO 파일"
git diff --name-status "$range" -- "$java/*/dto/*.java" || true

section "DTO 에서 지워지거나 바뀐 줄 (필드 삭제, 개명, 타입 변경 후보)"
git diff -U0 "$range" -- "$java/*/dto/*.java" \
  | grep -E '^(\+\+\+ b/|-[^-])' \
  | grep -v -E '^-\s*(import |//|\*|/\*|$)' \
  | grep -B1 -E '^-' | grep -v '^--$' || echo "(없음)"

section "에러 코드 (ErrorCase) 추가, 삭제, 변경"
git diff -U0 "$range" -- "$java/*ErrorCase.java" \
  | grep -E '^(\+\+\+ b/|[+-]\s+[A-Z][A-Z0-9_]+\()' || echo "(없음)"

section "인증, 응답 wrapper, 예외 처리, 날짜 포맷, 앱 버전 판정"
git diff --name-status "$range" -- \
  "$java/*/auth/*" "$java/*/common/auth/*" "$java/*/common/exception/*" \
  "$java/*/common/response/*" "$java/*/common/web/*" "$java/*/common/config/DateFormatter.java" \
  "$java/*/config/*Jackson*" || true
git diff -U0 "$range" -- "$java/*.java" \
  | grep -E '^[+-][^+-].*(@JsonFormat|@JsonProperty|@JsonIgnore|@JsonInclude|DateTimeFormatter\.ofPattern)' || true

section "페이지네이션, 정렬 (커서, limit, orderBy)"
git diff -U0 "$range" -- "$java/*Repository*.java" "$java/*/common/response/*" \
  | grep -E '^(\+\+\+ b/|[+-][^+-].*(\.limit\(|orderBy\(|hasNext|cursor|Pageable|Sort\.))' \
  | grep -B1 -E '^[+-][^+-]' | grep -v '^--$' || echo "(없음)"

section "Flyway 마이그레이션"
git diff --name-status "$range" -- 'src/main/resources/db/migration/V*.sql' || true
printf '범위 끝 최고 버전: '
git ls-tree --name-only "${range##*..}" src/main/resources/db/migration/ \
  | grep -oE 'V[0-9]+' | sort -V | tail -1

section "알림, 배치, 메시지 큐 (실사용자 푸시와 스케줄)"
git diff --name-status "$range" -- \
  "$java/*fcm*" "$java/*Notification*" "$java/*Reminder*" "$java/*Scheduler*" \
  "$java/*Job.java" "$java/*quartz*" "$java/*rabbitmq*" || true
git diff -U0 "$range" -- "$java/*.java" | grep -E '^[+-][^+-].*(cron|CronScheduleBuilder|withIntervalIn)' || true

section "배포 설정 (workflow, compose, Dockerfile, build.gradle)"
git diff --name-status "$range" -- .github/workflows docker-compose.prod.yml docker-compose.dev.yml Dockerfile build.gradle || true
