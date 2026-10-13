#!/usr/bin/env bash
set -euo pipefail

fail=0

error() {
  printf 'ERROR: %s\n' "$1" >&2
  fail=1
}

require_file() {
  if [ ! -f "$1" ]; then
    error "missing required file: $1"
  fi
}

primary_commands=(
  analysis
  check
  commit
  explain
  feat
  migrate
  perf
  plan
  pr
  report
  test
)

for command in "${primary_commands[@]}"; do
  require_file ".claude/commands/${command}.md"
  require_file ".codex/skills/${command}/SKILL.md"
  require_file ".codex/skills/${command}/agents/openai.yaml"
done

require_file ".codex/skills/perf-audit/SKILL.md"
require_file ".codex/skills/test-writer/SKILL.md"

# 팀 공유 Claude Code 설정과 훅. settings.local.json 은 개인 파일이라 여기서 보지 않는다.
require_file ".claude/settings.json"
require_file ".claude/hooks/db_safety_check.py"
require_file ".claude/hooks/compile_check.sh"
require_file ".claude/hooks/test_db_safety_check.py"

if [ -f ".claude/settings.json" ] && ! python3 -m json.tool ".claude/settings.json" >/dev/null 2>&1; then
  error ".claude/settings.json is not valid JSON"
fi

if [ -f ".claude/hooks/test_db_safety_check.py" ] && ! python3 ".claude/hooks/test_db_safety_check.py" >/dev/null 2>&1; then
  error "db_safety_check hook tests failed: run python3 .claude/hooks/test_db_safety_check.py"
fi

if git ls-files --error-unmatch ".claude/settings.local.json" >/dev/null 2>&1; then
  error ".claude/settings.local.json is personal and must not be tracked"
fi

for required in "사용자 영향" "API 호환성" "DB migration" "인증/권한" "검증 결과" "배포 리스크" "롤백"; do
  if ! grep -q "$required" .github/PULL_REQUEST_TEMPLATE.md; then
    error "PR template is missing section text: $required"
  fi
done

if grep -q '^/AGENTS\.md$' .gitignore; then
  error "AGENTS.md is ignored; shared agent guidance should be trackable"
fi

# src/test/resources/application-test.yml 은 예외다. CI 테스트가 이 파일로 Testcontainers 환경을 띄워서
# 커밋해야 하고(ddf9c69), 값은 전부 테스트 전용 더미라 운영 설정과 겹치지 않는다.
# 아래 secret_hits 검사는 이 파일에도 그대로 적용된다.
tracked_secret_files="$(
  git ls-files \
    | grep -E '(^|/)(\.env(\..*)?|FirebaseAdminKey\.json|application(-(dev|prod|local|test))?\.ya?ml|application(-(dev|prod|local|test))?\.properties)$' \
    | grep -v -x 'src/test/resources/application-test\.yml' || true
)"

if [ -n "$tracked_secret_files" ]; then
  printf '%s\n' "$tracked_secret_files" >&2
  error "secret/config files are tracked"
fi

secret_hits="$(
  git grep -n -E -- \
    '-----BEGIN (RSA |EC |OPENSSH |PRIVATE )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}' \
    -- ':!*.md' ':!.codex/scripts/agent-config-lint.sh' || true
)"

if [ -n "$secret_hits" ]; then
  printf '%s\n' "$secret_hits" >&2
  error "possible secret material found in tracked files"
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi

printf 'Agent configuration lint passed.\n'
