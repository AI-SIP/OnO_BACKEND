# OnO 백엔드 — CLAUDE.md

## Operating constraints

### 자유롭게 해도 되는 것
- git 작업 자유: 커밋·push·브랜치·머지 등 (소유자 판단).
- 인프라 작업 자유: Docker 배포, 설정 변경, 인프라 리소스.

### 실사용자 서비스 — 프로덕션 안전 우선
- **라이브 서비스를 깨지 말 것.** 되돌리기 어렵거나 사용자에게 영향 가는 변경(배포·스키마 변경·데이터 마이그레이션)은 실행 전 영향 범위를 설명하고 확인받는다.
- **dev 서버에서 먼저 검증 후 prod 반영.** 프로덕션 MySQL 파괴·마이그레이션 신중.
- **FCM = 실사용자 푸시 발송.** 발송 경로를 수정하거나 실행하기 전 반드시 경고하고 확인받는다.
- 프로덕션 DB에 대한 삭제/대량 수정/truncate는 명시적 확인 없이 실행하지 않는다.
- 되돌리기 어려운/외부로 나가는 작업은 한 번 확인.

### 작업 방식
- 커밋 메시지 형식:
  ```
  [Feat] 한 줄 요약
  - 상세 1
  - 상세 2
  ```
  태그: `[Feat]` / `[Fix]` / `[Refactor]` / `[Chore]` / `[Test]` / `[Docs]` / `[Perf]`
- 장시간 작업은 중간 진행 공유.
- **응답 언어: 한국어.** 주석·커밋도 한국어.
- 근거는 반드시 `파일:라인` 형식으로 명시. 추정 금지.

## 빌드 / 테스트
```bash
./gradlew compileJava                          # 엔티티 변경 후 컴파일 확인
./gradlew test --tests "패키지.클래스.메서드"  # 특정 테스트 실행
./gradlew build                                # 전체 빌드
```

## 프로젝트 구조

**스택**: Spring Boot + MySQL + Docker, dev/prod 환경 분리  
**루트 패키지**: `com.aisip.OnO.backend`

| 패키지 | 도메인 |
|---|---|
| `auth` | 인증 (OAuth, JWT) |
| `user` | 사용자 계정·프로필 |
| `problem` | 오답 문제 등록·조회·수정·삭제 |
| `practicenote` | 복습 기록 |
| `folder` | 폴더 (문제 분류) |
| `tag` | 태그 |
| `mission` | 미션·보상 |
| `problemsolve` | 문제 풀이 기록 |
| `learningcalendar` | 학습 캘린더 |
| `learningreport` | 학습 리포트 |
| `admin` | 관리자 기능 |
| `common` | 공통 유틸·예외·응답 |
| `config` | 설정 (Security, JPA 등) |
| `util` | 유틸리티 |

**핵심 불변식**: 모든 데이터 접근은 `userId` 기준 소유권 검증 필수 — 사용자는 자신의 오답노트 데이터만 접근 가능.

## 작업 파이프라인

```
일반 기능:  analysis → plan → feat → test → check → commit → report
성능 작업:  perf(진단) → plan → feat → test → check → commit → report
```
- 단계 경계에서 사람이 검토 후 다음으로 진행 (자동 연쇄 X).
- `analysis`는 요구 명확하면 생략 가능. `explain`은 아무 때나.
- 검토 강도: plan 후 🔴최고 / feat 후 🟠 / check 결과 🟡 / report 🟢.