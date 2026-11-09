#!/usr/bin/env python3
"""
PreToolUse 훅: 데이터를 되돌릴 수 없게 지우는 DB 작업을 실행 전에 막는다.

- Bash: SQL 을 실제로 실행하는 명령(mysql/mysqlsh/mariadb 클라이언트, docker exec 안의 클라이언트,
  flyway CLI, gradle flyway 태스크)일 때만 SQL 문장을 검사한다. `git log --grep "DROP TABLE"` 이나
  `grep` 처럼 SQL 을 실행하지 않는 명령은 검사하지 않는다.
- MCP(mcp__mysql*): tool_input 의 sql(없으면 query) 필드를 항상 검사한다.
- 차단하면 stderr 에 이유를 쓰고 exit 2 로 끝낸다. Claude 는 stderr 를 보고 왜 막혔는지 안다.

표준 라이브러리만 쓴다. 테스트: python3 .claude/hooks/test_db_safety_check.py
"""
import json
import os
import re
import sys

SQL_CLIENTS = {"mysql", "mysqlsh", "mariadb"}
GRADLE_COMMANDS = {"gradlew", "gradle", "mvn", "mvnw"}
CONTAINER_COMMANDS = {"docker", "kubectl", "podman"}
MAX_SQL_FILE_BYTES = 5 * 1024 * 1024

# 명령 첫 단어 앞에 올 수 있는 래퍼. 이것들을 건너뛰고 실제 실행 파일을 본다.
COMMAND_WRAPPERS = {"sudo", "env", "time", "nohup", "exec", "command"}

# 셸에서 명령이 새로 시작되는 자리. 따옴표도 넣어서 bash -c "mysql ..." 안쪽까지 본다.
SEGMENT_SPLIT = re.compile(r"&&|\|\||[;&|()`\n\"']|\$\(")
ENV_ASSIGN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=")
SQL_FILE_TOKEN = re.compile(r"[\w./~@+-]*\.sql\b")

DDL_AUTO_ARG = re.compile(
    r"(?:--|-D)[\w.-]*ddl[-_]auto\s*=\s*['\"]?(?:create-drop|create|update)\b"
    r"|SPRING_JPA_HIBERNATE_DDL_AUTO\s*=\s*['\"]?(?:create-drop|create|update)\b",
    re.IGNORECASE,
)
FLYWAY_CLEAN = re.compile(r"\bflyway\b[^;&|\n]*\bclean\b|\bflywayClean\b|\bflyway:clean\b", re.I)
FLYWAY_TASK = re.compile(r"\bflyway\w*\b|\bflyway:\w+", re.I)
COMPOSE_DOWN_VOLUMES = re.compile(
    r"\bdocker(?:\s+compose|-compose)\b[^;&|\n]*\bdown\b[^;&|\n]*\s(?:-v|--volumes)\b", re.I
)
PROD_TARGET = re.compile(r"\b(?:prod|production)\b", re.I)

STATEMENT_RULES = [
    (re.compile(r"\bDROP\s+(?:TEMPORARY\s+)?(DATABASE|SCHEMA|TABLE)\b", re.I), "DROP {0}"),
    (re.compile(r"\bTRUNCATE\s+(?:TABLE\s+)?[`\w]", re.I), "TRUNCATE"),
    (re.compile(r"\bALTER\s+TABLE\b.*\bDROP\b", re.I), "ALTER TABLE ... DROP"),
]
DELETE_STMT = re.compile(r"\bDELETE\b.*\bFROM\b", re.I)
# INSERT ... ON DUPLICATE KEY UPDATE 는 SET 이 없어서 걸리지 않지만, 혹시 몰라 KEY 뒤 UPDATE 는 뺀다.
UPDATE_STMT = re.compile(r"(?<!KEY )\bUPDATE\b\s+.+?\bSET\b", re.I)
WHERE = re.compile(r"\bWHERE\b", re.I)


def normalize_sql(text):
    """주석을 지우고 공백과 줄바꿈을 한 칸으로 모은다. 주석 안의 WHERE 는 WHERE 로 치지 않는다."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"(?m)(?:--\s|(?:^|(?<=\s))#).*$", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def check_sql(text):
    """SQL 덩어리를 ; 로 나눠 문장마다 검사하고, 걸린 이유 목록을 돌려준다."""
    reasons = []
    for statement in normalize_sql(text).split(";"):
        statement = statement.strip()
        if not statement:
            continue
        for pattern, label in STATEMENT_RULES:
            match = pattern.search(statement)
            if match:
                reasons.append(label.format(*(g.upper() for g in match.groups())))
        if DELETE_STMT.search(statement) and not WHERE.search(statement):
            reasons.append("WHERE 없는 DELETE")
        if UPDATE_STMT.search(statement) and not WHERE.search(statement):
            reasons.append("WHERE 없는 UPDATE")
    return reasons


def command_words(segment):
    words = segment.split()
    while words and (words[0] in COMMAND_WRAPPERS or ENV_ASSIGN.match(words[0])):
        words.pop(0)
    return words


def executes_sql(command):
    """SQL 을 실제로 실행하는 명령인지 본다. 돌려주는 값: (SQL 클라이언트 호출 여부, flyway 실행 여부)."""
    client = flyway = False
    for segment in SEGMENT_SPLIT.split(command):
        words = command_words(segment)
        if not words:
            continue
        head = os.path.basename(words[0])
        if head in SQL_CLIENTS:
            client = True
        elif head in CONTAINER_COMMANDS and "exec" in words:
            if {os.path.basename(w) for w in words} & SQL_CLIENTS:
                client = True
        elif head == "flyway":
            flyway = True
        elif head in GRADLE_COMMANDS and FLYWAY_TASK.search(segment):
            flyway = True
    return client, flyway


def read_sql_files(command, cwd):
    """명령에 나온 .sql 파일(< file.sql, cat file.sql | mysql, source file.sql)의 내용을 읽는다."""
    contents = []
    for token in sorted(set(SQL_FILE_TOKEN.findall(command))):
        path = os.path.expanduser(token)
        if not os.path.isabs(path):
            path = os.path.join(cwd, path)
        try:
            if os.path.isfile(path) and os.path.getsize(path) <= MAX_SQL_FILE_BYTES:
                with open(path, encoding="utf-8", errors="replace") as f:
                    contents.append(f.read())
        except OSError:
            continue
    return contents


def check_bash(command, cwd):
    reasons = []
    if DDL_AUTO_ARG.search(command):
        reasons.append("ddl-auto=create/create-drop/update 실행 인자")
    if COMPOSE_DOWN_VOLUMES.search(command):
        reasons.append("docker compose down -v (DB 볼륨 삭제)")

    client, flyway = executes_sql(command)
    if flyway and FLYWAY_CLEAN.search(command):
        reasons.append("Flyway clean")
    if not client and not flyway:
        return reasons

    if client and PROD_TARGET.search(command):
        reasons.append("운영(prod) 대상으로 보이는 SQL 클라이언트 접속")
    reasons.extend(check_sql(command))
    for content in read_sql_files(command, cwd):
        reasons.extend(check_sql(content))
    return reasons


def evaluate(data):
    """훅 입력 JSON 을 받아 (차단 이유 목록, 검사한 원문) 을 돌려준다."""
    tool_name = data.get("tool_name") or ""
    tool_input = data.get("tool_input") or {}
    cwd = data.get("cwd") or os.getcwd()

    if tool_name.startswith("mcp__"):
        sql = tool_input.get("sql") or tool_input.get("query") or ""
        return (check_sql(sql) if isinstance(sql, str) else []), sql
    command = tool_input.get("command") or ""
    return check_bash(command, cwd), command


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0
    reasons, source = evaluate(data)
    if not reasons:
        return 0
    unique = list(dict.fromkeys(reasons))
    print(f"[db_safety_check] 위험한 DB 작업이라 실행을 막았습니다: {', '.join(unique)}", file=sys.stderr)
    print(f"대상: {str(source)[:300]}", file=sys.stderr)
    print(
        "데이터를 되돌릴 수 없게 지울 수 있는 작업입니다. 다른 방법으로 우회하지 말고, 영향 범위와 "
        "롤백 방법을 사용자에게 설명한 뒤 사용자가 직접 실행하도록 안내하세요.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
