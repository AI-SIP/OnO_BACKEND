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

# 스킬 본문은 .agents/skills/<name>/SKILL.md 한 벌만 둔다.
# - Codex 는 레포의 .agents/skills 를 직접 읽는다.
# - Claude Code 는 .claude/skills/<name> 심볼릭 링크로 같은 디렉터리를 읽는다.
# - Claude 전용 frontmatter(argument-hint, disable-model-invocation)는 Codex 가 무시한다.
primary_skills=(
  analysis
  check
  commit
  explain
  feat
  issue
  migrate
  perf
  plan
  pr
  report
  test
  release-check
  deploy-verify
)

# 모델이 스스로 부르면 안 되는 스킬(커밋, push, 이슈 생성, 파일 저장처럼 부수효과가 있는 것)
manual_only_skills=(commit feat issue migrate pr release-check deploy-verify)

# SKILL.md 의 frontmatter 를 YAML 로 읽어 "키=값" 줄로 낸다. 파싱에 실패하면 0 이 아닌 값으로 끝난다.
read_frontmatter() {
  ruby -ryaml -e '
    text = File.read(ARGV[0], encoding: "UTF-8")
    abort "frontmatter must start at line 1" unless text.start_with?("---\n")
    fm = text.split(/^---[ \t]*$/, 3)[1] or abort "frontmatter is not closed"
    data = YAML.safe_load(fm)
    abort "frontmatter is not a mapping" unless data.is_a?(Hash)
    data.each { |k, v| puts "#{k}=#{v.to_s.gsub("\n", " ")}" }
  ' "$1" 2>/dev/null
}

read_implicit_policy() {
  ruby -ryaml -e '
    data = YAML.safe_load(File.read(ARGV[0], encoding: "UTF-8"))
    puts data.dig("policy", "allow_implicit_invocation").to_s
  ' "$1" 2>/dev/null
}

is_manual_only() {
  local target="$1" s
  for s in "${manual_only_skills[@]}"; do
    [ "$s" = "$target" ] && return 0
  done
  return 1
}

for skill in "${primary_skills[@]}"; do
  require_file ".agents/skills/${skill}/SKILL.md"
  require_file ".agents/skills/${skill}/agents/openai.yaml"

  link=".claude/skills/${skill}"
  if [ ! -L "$link" ]; then
    error "$link must be a symlink to ../../.agents/skills/${skill}"
  elif [ "$(readlink "$link")" != "../../.agents/skills/${skill}" ]; then
    error "$link points to $(readlink "$link"), expected ../../.agents/skills/${skill}"
  fi
done

for dir in .agents/skills/*/; do
  skill="$(basename "$dir")"
  skill_md="${dir}SKILL.md"
  [ -f "$skill_md" ] || { error "missing $skill_md"; continue; }

  if ! frontmatter="$(read_frontmatter "$skill_md")"; then
    error "$skill_md frontmatter is not valid YAML"
    continue
  fi

  name="$(printf '%s\n' "$frontmatter" | sed -n 's/^name=//p')"
  description="$(printf '%s\n' "$frontmatter" | sed -n 's/^description=//p')"
  disabled="$(printf '%s\n' "$frontmatter" | sed -n 's/^disable-model-invocation=//p')"

  [ "$name" = "$skill" ] || error "$skill_md name '$name' must match directory '$skill'"
  [ -n "$description" ] || error "$skill_md description is empty"

  # Claude 쪽 자동 호출 차단과 Codex 쪽 암묵 호출 정책이 서로 맞아야 한다.
  openai_yaml="${dir}agents/openai.yaml"
  if [ -f "$openai_yaml" ]; then
    implicit="$(read_implicit_policy "$openai_yaml")" || { error "$openai_yaml is not valid YAML"; continue; }
    if [ "$disabled" = "true" ] && [ "$implicit" != "false" ]; then
      error "$skill: disable-model-invocation is true but $openai_yaml allow_implicit_invocation is not false"
    fi
    if [ "$disabled" != "true" ] && [ "$implicit" = "false" ]; then
      error "$skill: $openai_yaml disallows implicit invocation but SKILL.md lacks disable-model-invocation: true"
    fi
  fi

  if is_manual_only "$skill" && [ "$disabled" != "true" ]; then
    error "$skill_md must set disable-model-invocation: true (side-effecting skill)"
  fi

  # .claude/skills 에 같은 이름의 실제 디렉터리가 있으면 본문이 두 벌이 된다.
  if [ -e ".claude/skills/${skill}" ] && [ ! -L ".claude/skills/${skill}" ]; then
    error ".claude/skills/${skill} duplicates .agents/skills/${skill}; replace it with a symlink"
  fi
done

for link in .claude/skills/*; do
  [ -L "$link" ] || continue
  [ -f "$link/SKILL.md" ] || error "$link is a broken skill symlink"
done

# 옛 위치가 다시 생기면 같은 절차가 두 벌로 갈라진다.
if [ -d ".codex/skills" ] && [ -n "$(ls -A .codex/skills 2>/dev/null)" ]; then
  error ".codex/skills is no longer used; put skills in .agents/skills and symlink them from .claude/skills"
fi

if [ -d ".claude/commands" ] && [ -n "$(ls -A .claude/commands 2>/dev/null)" ]; then
  error ".claude/commands is no longer used; add a skill under .agents/skills instead"
fi

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
