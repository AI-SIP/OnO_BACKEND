---
name: test
description: OnO 테스트 작성 및 실행 요청에 사용. 사용자가 "test" 명령을 요청하면 test-writer 원칙에 따라 기존 테스트 구조를 확인하고 필요한 테스트를 작성/실행한다. 인자가 없으면 현재 git 변경사항을 대상으로 한다.
---

# OnO Test

## Workflow

1. 대상이 없으면 `git status`와 `git diff HEAD` 기준의 현재 변경사항으로 테스트 대상을 좁힌다.
2. 기존 테스트 구조, test profile, fixture, 인증 helper, DB 격리 방식을 확인한다.
3. 필요한 테스트를 작성하거나 보강한다.
4. 사용자 격리, 권한 실패, validation 실패, null/empty, 예외 케이스를 위험도에 맞춰 포함한다.
5. 외부 연동(FCM, 메일 등)은 mock 또는 fake로 검증한다.
6. 관련 테스트만 우선 실행하고, 실패하면 원인 파악 후 수정한다.

## Guardrails

- MySQL/PostgreSQL 환경에 `ddl-auto=create` 또는 `create-drop`을 추가하지 않는다.
- 기존 저품질 테스트를 그대로 복붙하지 않는다.
- 테스트 실행이 어렵다면 이유와 대체 검증을 보고한다.
