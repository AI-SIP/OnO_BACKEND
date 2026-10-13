---
name: release-check
description: develop 을 main(운영)에 배포하기 전에, 배포 범위의 API 계약 변경이 출시된 프론트 앱과 충돌하는지 교차 확인하고 배포 가능/주의/불가 판정 보고서를 쓴다. 읽기 전용.
disable-model-invocation: true
argument-hint: "[git 범위, 기본 origin/main..origin/develop]"
allowed-tools: Bash(bash .claude/skills/release-check/extract-changes.sh*) Bash(git fetch *) Bash(git log *) Bash(git show *) Bash(git diff *) Read Grep Glob
---

# /release-check — 배포 전 프론트 호환성 검증

범위: `$ARGUMENTS` (비어 있으면 `origin/main..origin/develop`)

`main` push 는 `ci-prod.yml` 이 바로 운영에 배포하고 중간에 멈출 지점이 없다. 그래서 머지 전에 이 절차로 판정한다.
2026-09 에 손으로 세 번 했던 검증(`docs/30-백엔드-prod-배포-전-프론트-4.0.0-호환성-검증`, `docs/백엔드-prod-배포-검증`,
`docs/develop-main-배포-전수감사`, 워크스페이스 기준)의 순서와 확인 항목을 옮긴 것이다.

## 지키는 것

- **읽기 전용이다.** 코드 수정, 커밋, 머지, workflow 실행, 운영 서버 호출, DB 접속을 하지 않는다.
- **프론트 레포는 체크아웃하지 않는다.** 사용자가 작업 중일 수 있어서 `git -C <front> show <ref>:<path>`, `git -C <front> grep <pattern> <ref> -- lib` 로만 읽는다.
- **근거는 `레포/파일:라인`.** 추정으로 판정하지 않는다. 확인하지 못한 것은 "확인 못 함"으로 적는다.

## 1. 기준선 잡기

1. 백엔드 변경 추출 (읽기 전용, `git fetch` 만 한다):
   ```bash
   bash .claude/skills/release-check/extract-changes.sh $ARGUMENTS
   ```
   "변경 없음" 이 나오면 여기서 끝내고 그렇게 보고한다.
2. 프론트 레포 위치: 워크스페이스 기준 `../../OnO_FRONT` (백엔드 레포 루트에서). 없으면 사용자에게 경로를 묻는다.
3. 출시 클라이언트 ref 를 정한다.
   ```bash
   FRONT=../../OnO_FRONT
   git -C $FRONT fetch origin --quiet --tags
   git -C $FRONT branch -r --list 'origin/release/*' --sort=-committerdate | head -3
   git -C $FRONT tag --sort=-creatordate | head -3
   for ref in origin/main <최신 release 브랜치>; do echo "$ref $(git -C $FRONT show $ref:pubspec.yaml | grep '^version:') $(git -C $FRONT log -1 --format='%h %ad' --date=short $ref)"; done
   ```
   - 비교 대상은 **`origin/main` 과 최신 `origin/release/*`** 둘이다. 버전이 다르면 둘 다 본다.
   - 스토어에 올라간 빌드가 브랜치 끝과 다를 수 있다(9월 전수감사에서 실제로 그랬다). Sentry 의 앱 release 나 사용자 확인으로 맞출 수 있으면 맞추고, 못 하면 보고서에 가정으로 적는다.
   - 앱이 `X-App-Version` 헤더를 보내는지 확인한다 (`lib/Service/Api/HttpService.dart`). 백엔드가 `AppVersionResolver` 로 동작을 가르는 곳이 있으면 헤더 유무에 따라 결과가 달라진다.

## 2. 축별 교차 확인

추출 결과를 축별로 나눠 **`api-contract-checker` 서브에이전트**에 맡긴다. 축이 많으면 병렬로 띄운다.
각 프롬프트에는 백엔드 범위, 해당 축의 추출 결과, 프론트 ref 두 개, "프론트는 `git show`/`git grep` 으로만 읽을 것" 을 넣는다.

| 축 | 백엔드에서 볼 것 | 프론트에서 볼 것 |
|---|---|---|
| 엔드포인트 | 매핑 경로, 메서드 삭제와 변경, 새 필수 `@RequestParam`, 인증 요구 변경 (`auth/config/SecurityConfig.java`) | `lib/Service/Api/**` 의 호출 경로와 메서드. 사라진 경로가 하나라도 호출되면 404/405 |
| 응답 DTO | 필드 삭제, 개명, 타입 변경, nullable 전환. 추가만이면 안전 | `lib/Model/**` 의 `fromJson`. `json['x']` 에 `??` 기본값이 있는지 |
| 요청 DTO | 새 필수 필드, `@Valid` 제약 추가 | 앱이 그 필드를 항상 보내는지 |
| 에러 코드 | `*ErrorCase.java` 번호, HTTP status 재배정, 삭제 | `lib/Util/ErrorMessageMapper.dart`, `lib/Service/Api/HttpService.dart` 의 errorCode 분기 |
| 인증 | `JwtTokenizer`, `JwtTokenFilter`, `AuthErrorCase` | 토큰 갱신 트리거 errorCode(과거 기준 1005), 강제 로그아웃으로 떨어지는 코드 대역 (`lib/Provider/TokenProvider.dart`, `lib/Provider/UserProvider.dart`) |
| 날짜와 페이징 | `@JsonFormat`, `DateFormatter`, 커서 조건, 정렬, `limit(size+1)`, `hasNext` | `DateTime.parse` 사용 여부, `lib/Model/Common/PaginatedResponse.dart` |
| 알림 | FCM payload 의 `type`, 발송 시각, cron | `lib/Util/NotificationService.dart` 의 type 분기 (모르는 type 이면 탭해도 이동 없음) |

같이 위임할 것:
- Flyway 파일이 있으면 **`migration-safety`** 에이전트. 과거에 걸린 항목: `SET SESSION lock_wait_timeout` 누락, `CHARACTER SET/COLLATE` 미명시 `MODIFY COLUMN`, 재실행 가드(`IF NOT EXISTS`, `information_schema`) 누락, 기존 중복 데이터로 실패하는 UNIQUE, 배포 헬스체크 120초(`ci-prod.yml`)를 넘길 인덱스 빌드.
- 알림, 배치, RabbitMQ 변경이 있으면 **`db-safety`** 에이전트. 과거에 걸린 항목: 발송 시점 시간대 가드 없음(자정 푸시), 만료 처리 없는 백로그, blue-green 겹침 구간에 구 컨테이너가 새 Quartz 트리거를 ERROR 로 만드는 문제.

서브에이전트 결과 중 판정을 바꾸는 항목(불가, 주의)은 **직접 원본 파일을 열어 다시 확인**한다.

## 3. 판정

| 판정 | 기준 |
|---|---|
| **불가** | 출시 클라이언트가 쓰는 경로나 필드의 삭제, 개명, 타입 변경 / 토큰 갱신이나 로그아웃 분기에 걸리는 에러 코드 재배정 / 실사용자에게 잘못 나가는 푸시 / 실패 시 재기동이 막히는 마이그레이션 |
| **주의** | 되던 동작이 새 4xx 로 막힘(앱이 처리하는지 확인) / 앱이 모르는 알림 type / 잠금 대기 위험이 있는 DDL / 클라이언트 영향은 없지만 운영 확인이 필요한 설정 |
| **가능** | 필드와 경로 추가만, 앱이 이미 기본값으로 읽는 필드 추가, 내부 리팩터 |

이 배포로 **오히려 고쳐지는 것**(예: 앱이 이미 기대하던 필드가 생김)도 따로 적는다.

## 4. 보고서

워크스페이스 `docs/<YYYY-MM-DD>-배포-전-호환성-검증/보고서.md` (백엔드 레포 기준 `../../docs/`) 에 쓴다. 한국어, `~다` 평서체.

```
# 백엔드 prod 배포 전 프론트 <버전> 호환성 검증

## 검증 기준선        표: 현재 prod(main sha, 날짜) / 배포 후보 / 커밋 수, Flyway 범위 / 프론트 ref 와 버전 / 배포 방식
## 결론              판정 한 줄(가능, 주의, 불가) + 이유 두세 줄
## 배포 전 반드시 처리  불가 항목. 근거 파일:라인, 실제로 벌어지는 경로, 조치
## 배포 전 판단 필요    주의 항목
## 축별 결과          표: 축 / 결과 / 근거
## 이 배포로 고쳐지는 것
## 배포 체크리스트     배포 전(읽기 전용 점검 SQL 은 사용자가 실행) / 배포 직후(/deploy-verify prod) / 롤백(앱 이미지만 되돌리는지)
## 확인하지 못한 것
```

마지막에 사용자에게 판정과 보고서 경로만 짧게 알린다. 머지나 배포는 사용자가 한다.
