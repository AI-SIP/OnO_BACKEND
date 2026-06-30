# OnO 백엔드 — Claude Code 사용 가이드

> **대상**: 이 레포에서 Claude Code를 사용하는 개발자  
> **설정 위치**: `.claude/` (커맨드·에이전트·권한) + `CLAUDE.md` (프로젝트 규칙)

---

## 목차

1. [설정 파일 구조](#1-설정-파일-구조)
2. [커스텀 커맨드 9종](#2-커스텀-커맨드-9종)
3. [전문가 에이전트 7종](#3-전문가-에이전트-7종)
4. [커맨드 ↔ 에이전트 연결 구조](#4-커맨드--에이전트-연결-구조)
5. [작업 파이프라인](#5-작업-파이프라인)
6. [권한 설정](#6-권한-설정)
7. [CLAUDE.md — 프로젝트 가드레일](#7-claudemd--프로젝트-가드레일)
8. [실전 사용 예시](#8-실전-사용-예시)

---

## 1. 설정 파일 구조

```
backend/
├── CLAUDE.md                          # 프로젝트 규칙 (매 세션 자동 로드)
├── .claude/
│   ├── settings.local.json            # 권한 allowlist/denylist
│   ├── commands/                      # 커스텀 슬래시 커맨드 (9종)
│   │   ├── analysis.md
│   │   ├── plan.md
│   │   ├── feat.md
│   │   ├── test.md
│   │   ├── check.md
│   │   ├── report.md
│   │   ├── perf.md
│   │   ├── commit.md
│   │   └── explain.md
│   └── agents/                        # 전문가 서브에이전트 (7종)
│       ├── code-tracer.md
│       ├── permission-auditor.md
│       ├── perf-reviewer.md
│       ├── logic-reviewer.md
│       ├── db-safety.md
│       ├── test-writer.md
│       └── report-writer.md
└── docs/
    ├── plans/                         # /plan 커맨드가 계획서를 저장하는 위치
    └── reports/                       # /report 커맨드가 보고서를 저장하는 위치
```

### 각 파일이 하는 일

| 파일 | 역할 | 로드 시점 |
|---|---|---|
| `CLAUDE.md` | 프로젝트 컨텍스트·가드레일·빌드 명령 | 매 세션 자동 |
| `commands/*.md` | `/커맨드명` 입력 시 실행할 프롬프트 템플릿 | 해당 커맨드 호출 시 |
| `agents/*.md` | 커맨드가 내부적으로 위임하는 전문 AI | 커맨드에서 호출 시 |
| `settings.local.json` | 승인 없이 실행 가능한 도구 범위 제한 | 매 세션 자동 |

---

## 2. 커스텀 커맨드 9종

Claude Code에서 `/커맨드명 인자` 형식으로 호출합니다.

### 사용 방법

```
/커맨드명                  # 인자 없이 (선택적 인자인 경우)
/커맨드명 작업 설명        # 자유 텍스트 인자
/커맨드명 abc1234          # 커밋 해시 등 특정 값
```

---

### `/analysis` — 원인 규명·분석

**목적**: 버그·이상 동작의 근본 원인을 코드에서 찾는다. 코드 수정 없음.

```
/analysis                              # 인자 없으면 3가지 질문을 먼저 물어봄
/analysis 문제 등록 시 500 에러 발생
```

**동작 흐름**:
1. 인자가 없으면 ① 증상 ② 원하는 결과 ③ 관련 경로·로그를 먼저 질문
2. 가설 3~5개 나열
3. `code-tracer` + 필요 시 검증 에이전트들을 **병렬**로 투입해 각 가설 독립 검증
4. 에이전트 결과를 교차 반증 (모순·공백 식별)
5. 근본 원인 + 확신도 결론

**산출물**: 요약·근거(`파일:라인`)·근본 원인·다음 단계 제안 (파일 저장 없음)

**읽기 전용** — 코드 수정·커밋 없음

---

### `/plan` — 구현 계획서

**목적**: 구현 전에 영향 범위·단계·위험 요소를 정리한다. 계획서를 파일로 저장.

```
/plan 문제 상세 조회 API에 태그 필터 추가
/plan 복습 기록 이미지 업로드 S3 마이그레이션
```

**동작 흐름**:
1. `code-tracer`로 관련 코드 조사 (필요 시 `permission-auditor`, `perf-reviewer`, `db-safety` 추가)
2. 계획서 작성 (목적 / 영향 범위 / 구현 단계 / 위험 요소 / 테스트 방향 / 커밋 분할 제안)
3. `docs/plans/<작업명>_plan.md`에 저장
4. **승인 대기** — "검토 후 `/feat`으로 진행" 안내

**읽기 전용** — 계획서 파일만 저장, 코드 수정 없음

> 🔴 plan → feat 사이의 검토가 가장 중요한 체크포인트

---

### `/feat` — 기능 구현

**목적**: 승인된 계획서를 바탕으로 실제 코드를 구현한다.

```
/feat 태그 필터 추가
/feat docs/plans/tag_filter_plan.md
```

**동작 흐름**:
1. `docs/plans/`에서 관련 계획서 탐색. 없으면 `code-tracer`로 파악 후 간략 계획 제시 → **승인 대기**
2. 계획 승인 전 구현 시작 금지
3. 승인 후 구현. 엔티티 변경 시 `./gradlew compileJava` 자동 실행
4. FCM·프로덕션 DB 파괴 작업 시 사전 경고
5. `./gradlew compileJava`로 최종 컴파일 확인
6. 논리 단위별 커밋 메시지 초안 제시 → 확인 후 커밋

---

### `/test` — 테스트 작성

**목적**: 변경된 코드에 대한 테스트를 작성하고 실행한다.

```
/test                                  # 현재 변경분 대상
/test ProblemService
/test com.aisip.OnO.backend.problem
```

**동작 흐름**:
1. 대상 도메인의 기존 테스트 품질 확인 → 좋으면 관례 따름, 아니면 새로 작성
2. `test-writer` 에이전트 투입:
   - test 프로필 + H2 격리 DB
   - **사용자 격리 케이스 필수**: 본인 데이터 성공 / 타인 데이터 차단 / 인증 없음 401
   - 단위 테스트 + DB 연동 테스트
   - FCM·S3 등 외부 연동은 mock
3. `./gradlew test --tests "대상클래스"` 실행 → 실패 시 수정

---

### `/check` — 다관점 검증

**목적**: 구현된 코드를 4가지 관점에서 동시에 검증한다. 코드 수정 없음.

```
/check                                 # 현재 git diff 대상
/check ProblemController
/check docs/plans/tag_filter_plan.md   # 특정 파일·계획서 대상
```

**동작 흐름**:
1. 4개 에이전트를 **동시에** 투입:
   - `permission-auditor`: userId 소유권 검증, 인증·인가 누락
   - `perf-reviewer`: N+1, JPA 풀스캔, 커넥션풀, 인덱스
   - `logic-reviewer`: 엣지/널, 동시성, 예외·롤백
   - `db-safety`: FCM 실발송, 잘못된 쓰기, 데이터 오염
2. 결과 교차 검토 (에이전트 간 충돌·누락 재확인)
3. 위험도 분류 보고

**판정 기호**:
- 🔴 즉시 수정 필요 (사용자 영향, 보안, 데이터 오염)
- 🟠 배포 전 수정 권장 (성능, 예외 미처리)
- 🟡 다음 이터레이션 (마이너 개선)
- 🟢 이상 없음

**읽기 전용** — 코드 수정 없음

---

### `/report` — 결과 보고서

**목적**: 작업 완료 후 공유용 보고서를 작성한다.

```
/report abc1234                        # 기준 커밋 해시부터 HEAD까지
```

**동작 흐름**:
1. `code-tracer`로 `<커밋>..HEAD` 범위 변경 분석
2. `report-writer` 에이전트로 보고서 작성 (존댓말, Before/After 표, 테스트 코드 내용 제외)
3. 파일명 제안 후 저장 위치 확인 → `docs/reports/<개발사항>_보고서.md` 저장

---

### `/perf` — 성능 병목 진단

**목적**: 특정 기능의 성능 문제를 진단하고 개선안 우선순위를 제시한다.

```
/perf 문제 목록 조회 API
/perf com.aisip.OnO.backend.problem.service
/perf 복습 기록 저장 흐름
```

**동작 흐름**:
1. `perf-reviewer` + `code-tracer` **병렬** 투입
   - `perf-reviewer`: N+1, fetch 전략, 풀스캔, 커넥션풀, 배치 누락
   - `code-tracer`: 호출 경로 전체 추적, 쿼리 실행 지점
2. 🔴/🟠/🟡 우선순위별 개선안 (문제 지점·원인·개선 방법·예상 효과)

**읽기 전용** — 진단만, 수정은 `/plan` → `/feat`으로

---

### `/commit` — 커밋 생성

**목적**: 현재 변경분을 논리 단위로 나누고 OnO 커밋 메시지 형식으로 실제 커밋까지 수행한다.

```
/commit                                # 현재 변경 전체 분석
/commit 태그 필터 + 성능 개선 함께 작업함
```

**동작 흐름**:
1. `git diff HEAD` + `git status`로 변경 전체 파악
2. 논리 단위 분할 (각 단위는 독립 빌드 가능, 단일 목적)
3. 단위별 커밋 메시지 결정
4. 관련 파일만 staging한 뒤 논리 단위별로 `git commit` 실행
5. 생성된 커밋 해시와 남은 변경사항 보고

**커밋 메시지 형식**:
```
[Feat] 한 줄 요약
- 상세 1
- 상세 2
```
태그: `[Feat]` / `[Fix]` / `[Refactor]` / `[Chore]` / `[Test]` / `[Docs]` / `[Perf]`

---

### `/explain` — 코드 흐름·원리 설명

**목적**: 특정 기능·클래스의 동작 원리와 설계 이유를 설명한다.

```
/explain 문제 등록 API
/explain ProblemService.createProblem
/explain JWT 인증 흐름
```

**동작 흐름**:
1. `code-tracer`로 Controller → Service → Repository → 쿼리까지 전체 추적
2. 요청 처리 순서 + 왜 이렇게 설계했는가 (`파일:라인` 포함)
3. 흐름이 복잡하면 텍스트 시퀀스 다이어그램 추가

**읽기 전용** — 코드 수정 없음. 언제든 호출 가능.

---

## 3. 전문가 에이전트 7종

커맨드가 내부적으로 호출하는 전문 서브에이전트입니다. 직접 호출하지 않고 커맨드를 통해 사용합니다.

| 에이전트 | 역할 | 도구 권한 | 호출 커맨드 |
|---|---|---|---|
| `code-tracer` | Controller→Service→Repository→쿼리 호출 경로 추적 | 읽기 전용 | analysis, plan, feat, explain, report, perf |
| `permission-auditor` | userId 소유권 검증·인증·인가 누락·필터 조건 검사 | 읽기 전용 | check, analysis |
| `perf-reviewer` | N+1·풀스캔·커넥션풀·배치 누락·인덱스 검사 | 읽기 전용 | check, perf, analysis |
| `logic-reviewer` | 엣지·널·동시성·예외·롤백·비즈 규칙 검사 | 읽기 전용 | check, analysis |
| `db-safety` | FCM 실발송·잘못된 쓰기·데이터 오염·S3 정합성 검사 | 읽기 전용 | check, analysis |
| `test-writer` | test 프로필+격리 DB, 사용자 격리 케이스 포함 테스트 작성 | 읽기+쓰기 | test |
| `report-writer` | 존댓말·Before/After 표·외부 공유용 보고서 작성 | 읽기+쓰기 | report |

### 검증 에이전트 상세

#### `permission-auditor` — OnO 핵심 보안 에이전트

가장 중요한 에이전트. **사용자는 자신의 오답노트 데이터만 접근 가능**이라는 불변식을 검증한다.

검사 항목:
- `findById()` 후 소유자 확인 없이 반환 (userId 검증 누락)
- `@AuthenticationPrincipal` 없이 데이터 반환
- 관리자 전용 엔드포인트에 일반 사용자 접근 가능
- 검색 조건 빈 값 → 전체 데이터 노출
- ID 변조로 다른 사용자 데이터 접근 가능

#### `db-safety` — FCM 담당 에이전트

FCM은 실사용자에게 즉시 도달하며 되돌릴 수 없다.

검사 항목:
- 개발/테스트 코드에서 실제 FCM 발송 경로를 타는 경우
- 조건 없이 전체 사용자에게 발송
- WHERE 조건 불충분한 UPDATE/DELETE
- S3 파일 삭제 후 DB 레코드 미삭제 (또는 반대)

---

## 4. 커맨드 ↔ 에이전트 연결 구조

```
/analysis ──┬── code-tracer (항상)
            └── permission-auditor, perf-reviewer, logic-reviewer, db-safety (필요 시)

/plan ───────┬── code-tracer (항상)
             └── permission-auditor, perf-reviewer, db-safety (위험 시)

/feat ───────── code-tracer (영향 범위 파악)

/test ───────── test-writer

/check ──────┬── permission-auditor  ┐
             ├── perf-reviewer       │ 4개 동시 병렬
             ├── logic-reviewer      │
             └── db-safety          ┘

/report ─────┬── code-tracer (변경 분석)
             └── report-writer (보고서 작성)

/perf ───────┬── perf-reviewer  ┐ 2개 동시 병렬
             └── code-tracer   ┘

/explain ────── code-tracer

/commit ─── (에이전트 없음, 경량 명령)
```

---

## 5. 작업 파이프라인

### 일반 기능 개발

```
analysis → plan → feat → test → check → commit → report
  (선택)     🔴     🟠     │       🟡      │         🟢
                          └── 통과 필수 ──┘
```

- **analysis**: 요구가 명확하면 생략 가능
- **plan** 후 🔴: 가장 중요한 검토 포인트. 사람이 직접 계획서 확인 후 진행
- **feat** 후 🟠: 구현 완료 후 바로 test → check
- **check** 후 🟡: 🔴·🟠 항목은 반드시 수정 후 재확인
- **report** 🟢: 완료 후 공유 필요 시

### 성능 개선 작업

```
perf(진단) → plan → feat → test → check → commit → report
```

### 단발성 작업

```
feat → check → commit
```

### 아무 때나 사용

- `/explain`: 코드 이해 필요할 때 언제든
- `/commit`: 커밋 메시지 정리 필요할 때

---

## 6. 권한 설정

`.claude/settings.local.json`에서 도구 실행 권한을 제어합니다.

### 자동 허용 (승인 팝업 없음)

| 도구 | 이유 |
|---|---|
| `Read`, `Grep`, `Glob` | 읽기 전용, 부작용 없음 |
| `git status/diff/log/show/branch` | 조회만, 변경 없음 |
| `ls`, `find` | 파일 탐색, 변경 없음 |
| `./gradlew compileJava` | 컴파일 확인, 안전 |
| `./gradlew test --tests` | 테스트 실행, 안전 |
| `./gradlew build` | 전체 빌드 |

### 차단 (실행 불가)

| 도구 | 이유 |
|---|---|
| `git push --force` | 원격 히스토리 파괴 위험 |
| `git reset --hard` | 로컬 변경 손실 위험 |
| `git clean` | 추적 안 된 파일 삭제 위험 |

### 승인 팝업이 뜨는 경우

allowlist에 없는 Bash 명령(실제 커밋, 파일 쓰기, 서버 시작 등)은 실행 전 팝업이 뜹니다. 승인하면 그 명령만 1회 실행됩니다.

---

## 7. CLAUDE.md — 프로젝트 가드레일

`CLAUDE.md`는 세션 시작 시 자동으로 로드되어 모든 작업에 적용됩니다.

### 핵심 가드레일 요약

**항상 지키는 것**:
- 라이브 서비스를 깨지 않는다
- dev에서 검증 후 prod 반영
- FCM 경로 수정·실행 전 사전 경고
- 프로덕션 DB 파괴 작업은 명시적 확인 후

**자유롭게 하는 것**:
- git 커밋·push·브랜치·머지
- Docker 배포·인프라 설정

**응답 방식**:
- 한국어 응답·주석·커밋
- 근거는 `파일:라인` 형식 (추정 금지)
- 장시간 작업은 중간 진행 공유

---

## 8. 실전 사용 예시

### 예시 1: 신규 기능 개발 (문제에 태그 복수 추가)

```
# 1. 계획 먼저
/plan 문제 등록 API에 태그 복수 추가 지원

# → docs/plans/태그복수추가_plan.md 생성
# → 계획서 검토 후 승인

# 2. 구현
/feat 문제 등록 태그 복수 추가

# → 컴파일 자동 확인
# → 커밋 전 요약 보고

# 3. 테스트
/test ProblemService

# → 사용자 격리 케이스 포함 테스트 작성 + 실행

# 4. 검증
/check

# → 4개 에이전트 병렬 검증
# → 🔴🟠 항목 있으면 수정 후 재확인

# 5. 커밋
/commit
```

### 예시 2: 버그 조사

```
# 원인 모를 때
/analysis 다른 사용자 문제가 조회되는 버그

# → 에이전트 병렬 조사 → 근본 원인 + 파일:라인
```

### 예시 3: 성능 개선

```
# 병목 진단
/perf 복습 기록 목록 조회

# → N+1 등 발견 → /plan으로 개선 계획 수립
```

### 예시 4: 코드 이해

```
# 언제든
/explain JWT 인증 흐름
/explain ProblemController.getProblemList
```

### 예시 5: 작업 보고서

```
# 기준 커밋 이후 변경사항 보고서
/report 7edc25a

# → docs/reports/문제등록개선_보고서.md 생성
```

---

## 자주 묻는 것

**Q: `/feat` 없이 직접 구현 요청해도 되나?**  
A: 됩니다. 다만 Claude는 계획 제시 후 승인을 기다립니다. 빠르게 가려면 `/plan` 먼저 해두면 더 매끄럽습니다.

**Q: `/check`는 언제 쓰나?**  
A: `/feat` 완료 직후 습관적으로. 특히 권한·FCM·쓰기 로직이 포함된 경우 필수입니다.

**Q: 에이전트를 직접 호출할 수 있나?**  
A: 기술적으로는 가능하지만, 커맨드를 통해 호출하는 것이 권장 방식입니다. 커맨드가 올바른 컨텍스트와 제약을 함께 전달합니다.

**Q: `docs/plans/`에 계획서가 쌓이면 어떻게 하나?**  
A: 완료된 작업의 계획서는 삭제하거나 `docs/plans/archive/`로 이동해도 됩니다.
