#!/usr/bin/env bash
set -u

sky_client="${1:-}"
sky_event="${2:-turn-ended}"
payload="${3:-}"

if [ -n "$sky_client" ] && [ -x "$sky_client" ]; then
  "$sky_client" "$sky_event" "$payload" >/dev/null 2>&1 &
fi

message="Codex 작업이 완료되었습니다."
title="Codex"

if ! /usr/bin/osascript -e "display notification \"$message\" with title \"$title\" sound name \"Glass\"" >/dev/null 2>&1; then
  printf '\a'
fi
