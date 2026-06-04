---
name: perf-audit
description: OnO 백엔드 API 성능, 느린 쿼리, N+1, 풀스캔, 커넥션 풀, Flutter rebuild/jank/메모리 문제가 의심될 때 사용한다.
---

# OnO Performance Audit

## Workflow

1. 사용자가 체감하는 느린 흐름과 호출 경로를 먼저 좁힌다.
2. 백엔드는 controller/service/repository/query/transaction 경계를 따라간다.
3. Flutter는 화면 진입, 상태 변경, async 경합, rebuild 범위를 따라간다.
4. 운영 영향 기준으로 우선순위를 나눈다.
   - 사용자 데이터 손상 또는 장애 가능성
   - API latency, DB 부하, 커넥션 고갈 가능성
   - UI jank, 중복 요청, 배터리/메모리 낭비
5. 개선안은 관측 가능한 근거와 함께 제시하고, 과한 구조 변경은 피한다.

## Backend Focus

- N+1, unbounded query, pagination 누락, index 부재, 풀스캔 가능성
- 트랜잭션 범위 과다/부족, lazy loading 도달 가능성
- 외부 호출, FCM/메일 실발송, retry 부재

## Flutter Focus

- 불필요한 rebuild, 큰 리스트의 비효율 렌더링, debounce/throttle 부재
- loading/error/empty 상태에서 중복 요청 또는 화면 멈춤
