# OnO Codex 사용법

이 폴더는 OnO 백엔드 레포에서 Codex를 일관된 방식으로 쓰기 위한 프로파일과 스크립트를 담는다. 스킬은 이 폴더가 아니라 `.agents/skills/` 에 있다.

## 1. 구성

```text
.agents/skills/<이름>/          # 스킬 정본. Codex 가 레포에서 직접 읽는다
├── SKILL.md                    # frontmatter + 절차
└── agents/openai.yaml          # 표시 이름, 암묵 호출 정책
.claude/skills/<이름>           # 위 디렉터리로 가는 심볼릭 링크 (Claude Code 용)
.codex/
├── review.config.toml
├── build.config.toml
├── USAGE.md
└── scripts/
    ├── agent-config-lint.sh    # 스킬 구조와 공유 설정 검사 (CI: agent-config-lint.yml)
    └── codex-notify-macos.sh
```

- Codex 는 현재 디렉터리부터 레포 루트까지의 `.agents/skills` 를 스캔하므로, 레포 안에서 실행하면 **복사 없이** 스킬이 잡힌다.
- 프로파일(`*.config.toml`)과 알림 스크립트는 여전히 `~/.codex` 로 복사해서 쓴다(7절).
- 예전에 `~/.codex/skills/` 로 복사해 둔 같은 이름의 스킬이 있으면 지운다. Codex 는 이름이 같은 스킬을 합치지 않고 둘 다 목록에 띄운다.

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

`$이름` 으로 명시 호출하거나 `/skills` 에서 고른다. 각 스킬이 하는 일과 절차는 `.agents/skills/<이름>/SKILL.md` 가 정본이라 여기에는 다시 적지 않는다. 요약 표는 `AGENTS.md` 5절에 있다.

```text
analysis check commit explain feat issue migrate perf plan pr report test
```

- **`commit`, `feat`, `issue`, `migrate`, `pr` 은 명시 호출 전용이다.** `allow_implicit_invocation: false` 라서 요청 내용이 비슷해도 Codex 가 스스로 고르지 않는다.
- 나머지는 요청이 description 과 맞으면 자동으로 쓰일 수 있다.
- 인자를 넣지 않으면 대부분 `git status`, `git diff HEAD` 기준의 현재 변경사항을 대상으로 한다.
- SKILL.md 의 `$ARGUMENTS` 는 Claude Code 가 슬래시 명령 인자로 바꾸는 자리다. Codex 에서는 그대로 보이므로 사용자 메시지에서 대상을 읽으라고 본문에 적어 두었다.
- 예전 보조 스킬 `test-writer` 는 `test` 에, `perf-audit` 는 `perf` 에 합쳤다.

예:

```text
$analysis 소셜 로그인 후 특정 사용자만 401이 나는 원인 분석해줘
$check abc1234 이후 변경사항 검토해줘
$plan 학습 기록 검색 API 필터 기능 구현 계획 작성해줘
$feat docs/plans/search_filter_plan.md 기준으로 구현해줘
$test 버그 재현 테스트를 먼저 추가해줘
$perf 오답노트 목록 조회 API 성능 병목 진단해줘
$commit
$pr
```

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
codex --profile-v2 build exec "docs/plans/<작업명>_plan.md 기준으로 구현하고 관련 테스트를 실행해줘"
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

스킬은 레포의 `.agents/skills` 를 바로 읽으므로 복사하지 않는다. 프로파일과 알림 스크립트만 `~/.codex` 로 복사한다.

```bash
cp .codex/review.config.toml ~/.codex/review.config.toml
cp .codex/build.config.toml ~/.codex/build.config.toml
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
$feat <작성된 명세서 경로>
```

마지막 검토:

```text
codex --profile-v2 review
/review
```

커밋 메시지 형식:

```text
[Feat] 한 줄 요약 (#이슈)
- 상세 1
- 상세 2
```

버그 수정:

```text
codex --profile-v2 build
$test 버그 재현 테스트를 먼저 추가해줘
```

성능 점검:

```text
codex --profile-v2 review
$perf 느린 API 후보를 쿼리와 트랜잭션 기준으로 점검해줘
```
