#!/usr/bin/env bash
# Stop 훅: 작업 트리에 바뀐 .java 가 있을 때만 compileJava 를 돌린다.
# 실패하면 에러 요약을 stderr 로 내고 exit 2 로 끝내서 Claude 가 멈추지 않고 고치게 한다.
# 성공하거나 검사할 것이 없으면 아무것도 출력하지 않고 exit 0.

input="$(cat)"

# 이미 Stop 훅 때문에 이어서 작업하는 중이면 다시 막지 않는다(무한 루프 방지).
stop_active="$(printf '%s' "$input" | python3 -c 'import json,sys
try:
    print(str(json.load(sys.stdin).get("stop_hook_active", False)).lower())
except Exception:
    print("false")' 2>/dev/null)"
if [ "$stop_active" = "true" ]; then
  exit 0
fi

# 세션이 워크트리로 옮겨 갔을 수 있어서 훅 입력의 cwd 를 먼저 쓴다.
cwd="$(printf '%s' "$input" | python3 -c 'import json,sys
try:
    print(json.load(sys.stdin).get("cwd") or "")
except Exception:
    print("")' 2>/dev/null)"
cd "${cwd:-${CLAUDE_PROJECT_DIR:-.}}" 2>/dev/null || exit 0
root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$root" || exit 0
[ -x ./gradlew ] || exit 0

# 추적 중인 파일의 수정분과 새로 만든(untracked) .java 를 모두 본다.
if [ -z "$(git status --porcelain --untracked-files=all -- '*.java' 2>/dev/null)" ]; then
  exit 0
fi

if output="$(./gradlew compileJava -q 2>&1)"; then
  exit 0
fi

{
  echo "[compile_check] 바뀐 .java 가 있어 ./gradlew compileJava 를 돌렸는데 실패했습니다. 아래 에러를 고친 뒤 마무리하세요."
  summary="$(printf '%s\n' "$output" | grep -E '\.java:[0-9]+: error:|error:|FAILURE|What went wrong' | head -30)"
  if [ -n "$summary" ]; then
    printf '%s\n' "$summary"
  else
    printf '%s\n' "$output" | tail -30
  fi
} >&2
exit 2
