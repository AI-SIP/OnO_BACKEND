#!/usr/bin/env python3
"""db_safety_check.py 회귀 테스트. 실행: python3 .claude/hooks/test_db_safety_check.py"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
HOOK = os.path.join(HERE, "db_safety_check.py")


def run_hook(payload):
    result = subprocess.run(
        [sys.executable, HOOK],
        input=json.dumps(payload),
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result.returncode, result.stdout, result.stderr


def bash(command, cwd=None):
    payload = {"tool_name": "Bash", "tool_input": {"command": command}}
    if cwd:
        payload["cwd"] = cwd
    return run_hook(payload)


def mcp(sql, field="sql"):
    return run_hook({"tool_name": "mcp__mysql-ono__mysql_query", "tool_input": {field: sql}})


class BlockedTest(unittest.TestCase):
    def assertBlocked(self, result, keyword=None):
        code, stdout, stderr = result
        self.assertEqual(code, 2, f"차단되어야 함. stderr={stderr!r}")
        self.assertIn("db_safety_check", stderr, "차단 사유는 stderr 로 나가야 함")
        self.assertEqual(stdout, "")
        if keyword:
            self.assertIn(keyword, stderr)

    def test_drop_table_via_mysql_e(self):
        self.assertBlocked(bash('mysql -u root -p ono -e "DROP TABLE problem"'), "DROP TABLE")

    def test_drop_database_and_schema(self):
        self.assertBlocked(bash("mysql -e 'drop database ono'"), "DROP DATABASE")
        self.assertBlocked(bash("mysql -e 'DROP SCHEMA ono'"), "DROP SCHEMA")

    def test_truncate(self):
        self.assertBlocked(bash('mysql ono -e "TRUNCATE TABLE problem"'), "TRUNCATE")
        self.assertBlocked(bash('mysql ono -e "truncate problem"'), "TRUNCATE")

    def test_alter_table_drop(self):
        self.assertBlocked(bash('mysql -e "ALTER TABLE problem DROP COLUMN memo"'), "ALTER TABLE")

    def test_delete_without_where(self):
        self.assertBlocked(bash('mysql -e "DELETE FROM problem"'), "WHERE 없는 DELETE")

    def test_update_without_where(self):
        self.assertBlocked(bash('mysql -e "UPDATE users SET name = 1"'), "WHERE 없는 UPDATE")

    def test_where_only_in_other_statement(self):
        cmd = 'mysql -e "DELETE FROM problem; SELECT * FROM users WHERE id = 1"'
        self.assertBlocked(bash(cmd), "WHERE 없는 DELETE")
        cmd = 'mysql -e "UPDATE users SET a = 1; DELETE FROM problem WHERE id = 1"'
        self.assertBlocked(bash(cmd), "WHERE 없는 UPDATE")

    def test_newline_inside_sql(self):
        self.assertBlocked(bash('mysql -e "DROP\nTABLE problem"'), "DROP TABLE")
        self.assertBlocked(bash('mysql -e "DELETE\n  FROM\n problem"'), "WHERE 없는 DELETE")

    def test_where_only_in_comment(self):
        self.assertBlocked(bash('mysql -e "DELETE FROM problem /* WHERE id = 1 */"'), "WHERE 없는 DELETE")

    def test_heredoc(self):
        cmd = "mysql -u root ono <<'SQL'\nUPDATE problem\nSET memo = NULL;\nSQL"
        self.assertBlocked(bash(cmd), "WHERE 없는 UPDATE")

    def test_docker_exec_mysql(self):
        cmd = 'docker exec -i ono-mysql mysql -uroot -p ono -e "TRUNCATE problem"'
        self.assertBlocked(bash(cmd), "TRUNCATE")

    def test_bash_c_wrapper(self):
        self.assertBlocked(bash("bash -c \"mysql -e 'DROP TABLE problem'\""), "DROP TABLE")

    def test_sql_file_redirect_is_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            with open(os.path.join(tmp, "danger.sql"), "w") as f:
                f.write("-- 정리\nDELETE FROM problem;\n")
            self.assertBlocked(bash("mysql -u root ono < danger.sql", cwd=tmp), "WHERE 없는 DELETE")
            self.assertBlocked(bash("cat danger.sql | mysql ono", cwd=tmp), "WHERE 없는 DELETE")

    def test_prod_target(self):
        self.assertBlocked(bash("mysql -h ono-prod-db -e 'SELECT 1'"), "운영")

    def test_flyway_clean(self):
        self.assertBlocked(bash("flyway -url=jdbc:mysql://localhost/ono clean"), "Flyway clean")
        self.assertBlocked(bash("./gradlew flywayClean"), "Flyway clean")

    def test_ddl_auto_argument(self):
        self.assertBlocked(bash("./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=create'"), "ddl-auto")
        self.assertBlocked(bash("java -Dspring.jpa.hibernate.ddl-auto=update -jar app.jar"), "ddl-auto")
        self.assertBlocked(bash("SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop java -jar app.jar"), "ddl-auto")

    def test_compose_down_volumes(self):
        self.assertBlocked(bash("docker compose -f docker-compose.local.yml down -v"), "docker compose down -v")

    def test_mcp_sql_field(self):
        self.assertBlocked(mcp("DELETE FROM problem"), "WHERE 없는 DELETE")
        self.assertBlocked(mcp("drop table users"), "DROP TABLE")

    def test_mcp_query_field(self):
        self.assertBlocked(mcp("TRUNCATE problem", field="query"), "TRUNCATE")


class AllowedTest(unittest.TestCase):
    def assertAllowed(self, result):
        code, stdout, stderr = result
        self.assertEqual(code, 0, f"통과해야 함. stderr={stderr!r}")
        self.assertEqual(stderr, "")

    def test_git_log_grep_is_not_sql(self):
        self.assertAllowed(bash('git log --grep="DROP TABLE" --oneline'))

    def test_grep_source_is_not_sql(self):
        self.assertAllowed(bash('grep -rn "DROP TABLE" src'))
        self.assertAllowed(bash('grep -rn "DELETE FROM" src | grep -i mysql'))

    def test_reading_migration_file_is_not_sql(self):
        self.assertAllowed(bash("cat src/main/resources/db/migration/V13__drop_study_session.sql"))

    def test_select(self):
        self.assertAllowed(bash('mysql -u root ono -e "SELECT * FROM problem WHERE user_id = 1"'))
        self.assertAllowed(mcp("SELECT COUNT(*) FROM problem"))

    def test_delete_and_update_with_where(self):
        self.assertAllowed(bash('mysql -e "DELETE FROM problem WHERE id = 1"'))
        self.assertAllowed(bash('mysql -e "UPDATE users SET name = \'a\' WHERE id = 1"'))
        self.assertAllowed(mcp("DELETE\nFROM problem\nWHERE id = 1"))

    def test_insert_on_duplicate_key_update(self):
        self.assertAllowed(mcp("INSERT INTO t (id, v) VALUES (1, 2) ON DUPLICATE KEY UPDATE v = 2"))

    def test_gradle_test_and_compose_up(self):
        self.assertAllowed(bash("./gradlew test --tests 'com.aisip.OnO.backend.problem.*'"))
        self.assertAllowed(bash("docker compose -f docker-compose.local.yml up -d"))

    def test_ddl_auto_validate_and_text_search(self):
        self.assertAllowed(bash("java -Dspring.jpa.hibernate.ddl-auto=validate -jar app.jar"))
        self.assertAllowed(bash('grep -rn "ddl-auto" src/main/resources'))

    def test_invalid_or_empty_input(self):
        result = subprocess.run([sys.executable, HOOK], input="not json", capture_output=True, text=True)
        self.assertEqual(result.returncode, 0)
        self.assertAllowed(run_hook({"tool_name": "Bash", "tool_input": {}}))


if __name__ == "__main__":
    unittest.main(verbosity=2)
