---
name: test
description: OnO 백엔드 변경분에 대한 테스트를 Testcontainers MySQL 기반으로 작성하고 해당 테스트만 실행한다. 테스트 작성이나 보강, 버그 재현 테스트, "test <대상>" 요청에 사용한다.
argument-hint: "[테스트 대상 — 없으면 현재 변경분]"
---

# test — 테스트 작성과 실행

## 대상

$ARGUMENTS

> Claude Code 는 위 자리에 슬래시 명령 인자를 넣는다. 비어 있거나 `$ARGUMENTS` 가 그대로 보이면 사용자 메시지에서 대상을 읽는다.

대상이 없으면 `git status`, `git diff HEAD` 의 현재 변경분으로 좁힌다.

## 단계

1. **기존 테스트 확인**: 대상 도메인의 기존 테스트와 `src/test/java/.../support/`(fixture, 인증 helper, DB 정리 방식)를 먼저 읽는다. 품질이 좋으면 관례를 따르고, 아니면 복붙하지 않고 새로 작성한다.
2. **작성** (Claude Code 에서는 `.claude/agents/test-writer` 에 맡긴다)
   - test 프로필 + Testcontainers MySQL(`support/TestContainers.java`). H2 는 쓰지 않는다. **Docker 가 켜져 있어야 한다.**
   - **사용자 격리 필수**: 다른 사용자의 데이터에 접근할 수 없음을 검증한다.
   - 권한 실패, validation 실패, null/empty, 경계, 예외 케이스를 위험도에 맞춰 넣는다.
   - 버그 수정이면 가능하면 수정 전에 실패하는 테스트부터 만든다.
   - 외부 연동(FCM, 메일 등)은 mock 이나 fake 로 대체한다. 실제 발송이나 운영 DB 접근은 하지 않는다.
   - `ddl-auto` 를 `create` / `create-drop` 으로 바꾸지 않는다.
3. **실행**: `./gradlew test --tests "대상클래스"` 로 작성한 테스트만 먼저 돌린다. 실패하면 원인을 찾아 고친다.
4. **보고**: 작성한 테스트, 실행 명령과 결과를 적는다. 실행이 어려웠다면 이유와 대체 검증을 적는다.
