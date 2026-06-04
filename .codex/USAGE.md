# OnO Codex 사용법

이 폴더는 OnO 백엔드 레포에서 Codex를 일관된 방식으로 쓰기 위한 설정과 스킬을 담는다.

## 1. 설치된 구성

```text
.codex/
├── review.config.toml
├── build.config.toml
├── scripts/codex-notify-macos.sh
└── skills/
    ├── analysis/
    ├── check/
    ├── commit/
    ├── feat/
    ├── plan/
    ├── report/
    ├── test/
    ├── test-writer/
    ├── perf/
    ├── perf-audit/
    └── explain/
```

실제 Codex CLI가 바로 읽는 위치는 `~/.codex`다. 현재 이 레포의 `.codex` 내용은 다음 위치에도 설치되어 있다.

```text
~/.codex/review.config.toml
~/.codex/build.config.toml
~/.codex/skills/analysis
~/.codex/skills/check
~/.codex/skills/commit
~/.codex/skills/feat
~/.codex/skills/plan
~/.codex/skills/report
~/.codex/skills/test
~/.codex/skills/test-writer
~/.codex/skills/perf
~/.codex/skills/perf-audit
~/.codex/skills/explain
~/.codex/scripts/codex-notify-macos.sh
```

## 2. 프로파일 사용

현재 로컬 Codex CLI 기준으로 별도 config 파일은 `--profile-v2`로 사용한다.

### 분석/검토 모드

코드를 수정하지 않고 계획, 검토, 설명, 위험 분석을 할 때 사용한다.

```bash
codex --profile-v2 review
```

설정 내용:

```toml
model_reasoning_effort = "xhigh"
sandbox_mode = "read-only"
approval_policy = "on-request"
```

사용 예:

```bash
codex --profile-v2 review
```

그 다음 Codex 안에서:

```text
check 현재 변경사항 위험 검토해줘
```

또는 빌트인 리뷰:

```text
/review
```

### 구현/테스트 모드

파일 수정, 테스트 작성, 빌드 검증이 필요한 작업에 사용한다.

```bash
codex --profile-v2 build
```

설정 내용:

```toml
model_reasoning_effort = "high"
sandbox_mode = "workspace-write"
approval_policy = "on-request"
```

사용 예:

```bash
codex --profile-v2 build
```

그 다음 Codex 안에서:

```text
feat docs/...의 구현 명세서를 기준으로 기능 구현해줘
```

## 3. 커스텀 명령어 스킬 사용법

스킬은 명시적으로 `$skill-name` 형태로 호출할 수 있고, 요청 내용이 description과 맞으면 자동으로 호출될 수 있다.

현재 `.claude/commands`와 맞춰 제공하는 주요 명령어 이름:

```text
analysis check commit explain feat perf plan report test
```

보조 스킬:

```text
test-writer perf-audit
```

공통 규칙:

- 커스텀 명령어에 인자를 넣지 않으면 기본적으로 `git status`와 `git diff HEAD` 기준의 현재 변경사항을 대상으로 수행한다.
- `analysis`, `check`, `feat`는 완료 직전에 반드시 아래 자가 반증 질문에 답한 뒤 결론 또는 구현 결과를 확정한다.

```text
내가 놓쳤을 가능성이 큰 것은?
이 변경이 실제 사용자에게 깨질 수 있는 경로는?
이 결론이 틀렸다면 어떤 조건 때문인가?
```

### analysis

문제 원인을 읽기 전용으로 분석할 때 사용한다.

```text
$analysis 소셜 로그인 후 특정 사용자만 401이 나는 원인 분석해줘
```

인자를 생략하면 현재 변경사항을 대상으로 분석한다.

```text
$analysis
```

수행 방식:

- 가능한 원인 가설을 3~5개로 나눈다.
- 관련 코드, 설정, 로그, git diff/log를 읽기 전용으로 확인한다.
- 각 가설을 `파일:라인` 근거로 지지하거나 반박한다.
- 완료 직전에 자가 반증 3문항에 답하고 결론을 수정할지 판단한다.
- 근본 원인, 확신도, 다음 단계를 보고한다.

### check

현재 변경사항이나 특정 커밋 이후 변경을 다관점으로 검토할 때 사용한다.

```text
$check 현재 변경사항 위험 검토해줘
```

인자를 생략해도 현재 변경사항을 대상으로 검토한다.

```text
$check
```

또는:

```text
$check abc1234 이후 변경사항 검토해줘
```

수행 방식:

- `git status`, `git diff`, 필요 시 `git log/show`로 변경 범위를 좁힌다.
- 사용자 데이터 격리, 권한, N+1, 풀스캔, 트랜잭션, FCM/메일 실발송, DB schema 안전성을 확인한다.
- 1차 결론을 다시 반증해 실제 도달 가능한 문제인지 확인한다.
- 완료 직전에 자가 반증 3문항에 답하고 위험도 판단을 수정할지 확인한다.
- 위험도 `🔴/🟠/🟡/🟢` 순서로 보고한다.

### commit

현재 변경사항의 커밋 단위와 메시지만 제안할 때 사용한다. 실제 커밋은 명시 요청 전까지 실행하지 않는다.

```text
$commit 현재 변경사항 커밋 단위 추천해줘
```

수행 방식:

- `git status`, `git diff HEAD`로 변경 전체를 파악한다.
- 서로 독립적인 논리 단위로 나눈다.
- OnO 커밋 메시지 형식으로 제안한다.

```text
[Feat] 구현 타이틀 - 구현 상세1 구현 상세2
```

### feat

승인된 계획서나 명확한 작업 설명을 기준으로 기능을 구현할 때 사용한다.

```text
$feat docs/<기능폴더>/<명세서>.md 기준으로 구현해줘
```

인자를 생략하면 현재 변경사항을 읽고 남은 구현, 정리, 검증 대상을 파악한다.

```text
$feat
```

수행 방식:

- 기준 구현 명세서를 먼저 확인한다.
- 명세서가 불명확하면 어떤 문서를 기준으로 할지 묻는다.
- API 계약, DTO, validation, 예외 처리, transaction, 권한, 성능, 데이터 정합성을 확인하며 구현한다.
- 가능한 범위에서 테스트, 빌드, 정적 검사를 실행한다.
- 완료 직전에 자가 반증 3문항에 답하고 추가 수정 또는 리스크 보고가 필요한지 확인한다.
- 완료 후 사용자 영향, 검증 결과, 남은 리스크, 추천 커밋 메시지를 보고한다.

### plan

구현 전 명세서를 작성할 때 사용한다. 코드는 수정하지 않고 `docs` 아래에 계획서를 만든다.

```text
$plan 학습 기록 검색 API 필터 기능 구현 계획 작성해줘
```

또는:

```text
plan 학습 기록 검색 API 필터 기능
```

수행 방식:

- 관련 코드와 문서를 먼저 확인한다.
- API 계약, 데이터 흐름, 에러 처리, 테스트 방법, 배포 리스크를 정리한다.
- `/Users/ksm/programing/sw_maestro/OnO_BACKEND/backend/docs` 아래 기능별 폴더에 markdown을 작성한다.

### test-writer

테스트 구현 원칙을 더 강하게 적용할 때 사용하는 보조 스킬이다.

```text
$test-writer 이번 변경에 대한 API 통합 테스트 추가해줘
```

수행 방식:

- 기존 테스트 구조와 test profile을 먼저 확인한다.
- validation 실패, 권한 실패, null/empty 입력, DB 반영을 위험도에 맞춰 검증한다.
- MySQL/PostgreSQL 환경에 `ddl-auto=create` 또는 `create-drop`을 추가하지 않는다.

### test

`.claude/commands/test.md`에 대응하는 테스트 작성/실행 명령이다.

```text
$test 현재 변경사항에 필요한 테스트 작성하고 실행해줘
```

수행 방식:

- 대상이 없으면 현재 git diff 기준으로 테스트 대상을 정한다.
- 기존 테스트 구조와 test profile을 확인한다.
- 사용자 격리, 권한 실패, validation 실패, null/empty, 예외 케이스를 위험도에 맞춰 포함한다.
- 관련 테스트만 우선 실행하고 결과를 보고한다.

### report

특정 커밋부터 현재까지의 작업 보고서나 PR 초안을 작성할 때 사용한다.

```text
$report abc1234 이후 변경사항 보고서 작성해줘
```

수행 방식:

- `git log`, `git diff`, `git status`로 커밋된 변경과 미커밋 변경을 구분한다.
- 변경사항 요약, 사용자 영향, 검증 결과, 배포 리스크를 정리한다.
- 저장 위치가 불명확하면 먼저 확인한다.

### perf

`.claude/commands/perf.md`에 대응하는 성능 병목 진단 명령이다.

```text
$perf 오답노트 목록 조회 API 성능 병목 진단해줘
```

수행 방식:

- 대상 기능의 호출 경로와 쿼리 실행 지점을 추적한다.
- N+1, fetch 전략, 풀스캔, pagination 누락, index 부재, 커넥션 풀 고갈 가능성을 확인한다.
- `🔴/🟠/🟡` 우선순위로 개선안을 보고한다.

### perf-audit

성능 문제를 더 넓게 점검하는 보조 스킬이다. `perf`와 같은 목적이지만 Flutter rebuild/jank까지 명시적으로 포함한다.

```text
$perf-audit 오답노트 목록 조회 API가 느릴 수 있는지 확인해줘
```

수행 방식:

- controller/service/repository/query 흐름을 따라간다.
- pagination, index, N+1, transaction 범위, 외부 호출을 확인한다.
- 개선안은 운영 영향과 적용 난이도 순서로 정리한다.

### explain

코드 흐름이나 설계 판단을 학습 목적으로 설명받을 때 사용한다.

```text
$explain StudyGroup 가입 요청 API 흐름 설명해줘
```

수행 방식:

- 진입점부터 service, repository, 응답 DTO까지 호출 경로를 추적한다.
- 중요한 근거는 파일과 라인으로 설명한다.
- API 계약, validation, 예외 처리, 트랜잭션, 보안 포인트를 함께 짚는다.

## 4. 빌트인 리뷰 사용

Codex CLI에는 리뷰 명령이 있다.

대화형 Codex 안에서:

```text
/review
```

터미널에서 비대화형으로:

```bash
codex --profile-v2 review review
```

OnO 백엔드 변경 검토 시에는 이런 기준을 함께 요청한다.

```text
사용자 데이터 격리, 권한 누락, N+1, 풀스캔, 트랜잭션, FCM/메일 실발송, DB schema 안전성 기준으로 위험도를 나눠 검토해줘. 발견한 문제는 파일과 라인 근거를 포함해줘.
```

## 5. codex exec 사용

반복 검증이나 릴리즈 게이트처럼 비대화형 실행이 필요할 때 사용한다.

```bash
codex --profile-v2 review exec "현재 변경을 OnO 위험 체크리스트로 검토하고, 사용자 데이터 손상이나 권한 누락 가능성이 있으면 명확히 보고해줘"
```

백엔드 배포 전 예:

```bash
codex --profile-v2 review exec "현재 변경을 데이터 격리, N+1, 풀스캔, 트랜잭션, FCM/메일 실발송, ddl-auto 변경 기준으로 검토해줘"
```

구현 작업을 비대화형으로 맡길 때는 `build`를 사용한다.

```bash
codex --profile-v2 build exec "docs/<기능폴더>/<명세서>.md 기준으로 구현하고 관련 테스트를 실행해줘"
```

운영 서비스 변경은 비대화형 구현보다 대화형 `build` 모드가 더 안전하다.

## 6. 작업 완료 알림

전역 config의 `notify`는 다음 래퍼를 사용한다.

```text
~/.codex/scripts/codex-notify-macos.sh
```

동작:

- 기존 Computer Use `turn-ended` 알림을 그대로 호출한다.
- 추가로 macOS 알림을 띄운다.
- macOS 알림 실패 시 터미널 벨로 폴백한다.

수동 테스트:

```bash
~/.codex/scripts/codex-notify-macos.sh \
  "/Users/ksm/.codex/computer-use/Codex Computer Use.app/Contents/SharedSupport/SkyComputerUseClient.app/Contents/MacOS/SkyComputerUseClient" \
  turn-ended \
  '{"type":"agent-turn-complete"}'
```

알림이 보이지 않으면 macOS 시스템 설정에서 Terminal, Codex, 또는 사용 중인 터미널 앱의 알림 권한을 확인한다.

## 7. 설정 동기화

이 레포의 `.codex`를 수정한 뒤 실제 Codex 설정에도 반영하려면 같은 내용을 `~/.codex`로 복사해야 한다.

```bash
cp .codex/review.config.toml ~/.codex/review.config.toml
cp .codex/build.config.toml ~/.codex/build.config.toml
cp -R .codex/skills/analysis ~/.codex/skills/
cp -R .codex/skills/check ~/.codex/skills/
cp -R .codex/skills/commit ~/.codex/skills/
cp -R .codex/skills/feat ~/.codex/skills/
cp -R .codex/skills/plan ~/.codex/skills/
cp -R .codex/skills/report ~/.codex/skills/
cp -R .codex/skills/test ~/.codex/skills/
cp -R .codex/skills/test-writer ~/.codex/skills/
cp -R .codex/skills/perf ~/.codex/skills/
cp -R .codex/skills/perf-audit ~/.codex/skills/
cp -R .codex/skills/explain ~/.codex/skills/
cp .codex/scripts/codex-notify-macos.sh ~/.codex/scripts/codex-notify-macos.sh
chmod +x ~/.codex/scripts/codex-notify-macos.sh
```

현재 전역 config 백업 파일:

```text
~/.codex/config.toml.bak.codex-setup-kit-20260604
```

## 8. 추천 작업 흐름

새 기능:

```text
codex --profile-v2 review
plan <기능 설명>
```

이후:

```text
codex --profile-v2 build
feat <작성된 명세서 경로>
```

마지막 검토:

```text
codex --profile-v2 review
/review
```

커밋 메시지 형식:

```text
[Feat] 구현 타이틀 - 구현 상세1 구현 상세2
```

버그 수정:

```text
codex --profile-v2 build
$test-writer 버그 재현 테스트를 먼저 추가하고 수정해줘
```

성능 점검:

```text
codex --profile-v2 review
$perf-audit 느린 API 후보를 쿼리와 트랜잭션 기준으로 점검해줘
```
