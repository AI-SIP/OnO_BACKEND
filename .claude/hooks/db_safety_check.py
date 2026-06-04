#!/usr/bin/env python3
"""
PreToolUse hook: Bash 명령에서 위험한 DB 작업 패턴 감지 시 차단.
Claude가 DROP/TRUNCATE/무조건 DELETE 등을 실행하려 할 때 경고 후 중단.
"""
import sys
import json
import re

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
cmd = data.get("tool_input", {}).get("command", "")

DANGEROUS = [
    (r"DROP\s+TABLE",                              "DROP TABLE"),
    (r"TRUNCATE\s+(TABLE\s+)?[`\w][`\w.]*",        "TRUNCATE"),
    (r"ALTER\s+TABLE\s+[`\w][`\w.]*\s+DROP",       "DROP COLUMN/INDEX"),
    (r"DROP\s+DATABASE",                            "DROP DATABASE"),
]

found = [label for pattern, label in DANGEROUS if re.search(pattern, cmd, re.IGNORECASE)]

# WHERE 없는 DELETE — 백틱·스키마 프리픽스 포함, WHERE 절 있으면 제외
if (re.search(r"DELETE\s+FROM", cmd, re.IGNORECASE)
        and not re.search(r"\bWHERE\b", cmd, re.IGNORECASE)):
    found.append("WHERE 없는 DELETE")

if found:
    print(f"🚨 위험한 DB 작업 감지: {', '.join(found)}")
    print(f"   명령: {cmd[:200]}")
    print("   프로덕션 영향 범위를 확인한 뒤 명시적으로 허용해 주세요.")
    sys.exit(2)