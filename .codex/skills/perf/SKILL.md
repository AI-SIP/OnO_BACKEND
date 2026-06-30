---
name: perf
description: OnO 성능 병목 진단 요청에 사용. 사용자가 "perf" 명령을 요청하면 perf-audit 원칙에 따라 읽기 전용으로 호출 경로와 쿼리/렌더링 병목을 분석한다. 인자가 없으면 현재 git 변경사항이 건드린 흐름을 대상으로 한다.
---

# OnO Perf

## Workflow

1. 대상 기능, 엔드포인트, 패키지, 화면을 확인한다. 인자가 없으면 `git status`와 `git diff HEAD` 기준으로 현재 변경사항이 건드린 흐름을 대상으로 삼는다.
2. 백엔드는 controller/service/repository/query 흐름과 transaction 경계를 추적한다.
3. Flutter는 화면 진입, 상태 변경, async 경합, rebuild 범위를 추적한다.
4. N+1, fetch 전략, 풀스캔, pagination 누락, index 부재, 커넥션 풀 고갈, 데드락 가능성을 확인한다.
5. 읽기 전용으로 진단하고 코드는 수정하지 않는다.

## Output

- 🔴 즉시 개선
- 🟠 배포 전 개선 권장
- 🟡 백로그

각 개선안은 `파일:라인`, 원인, 개선 방법, 예상 효과를 포함한다.
