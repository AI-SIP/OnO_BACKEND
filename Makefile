# 로컬 개발 편의 명령.
# 인프라(MySQL/Redis/RabbitMQ)를 하나로 묶어 관리한다.

COMPOSE := docker compose -f docker-compose.local.yml
SERVICES := mysql redis rabbitmq

.DEFAULT_GOAL := help
.PHONY: help up down restart status logs ps test test-only coverage

help: ## 사용 가능한 명령 보기
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## 로컬 인프라 띄우기 (전부 healthy 될 때까지 대기)
	@$(COMPOSE) up -d --wait
	@$(MAKE) --no-print-directory status

down: ## 로컬 인프라 내리기 (볼륨은 유지되어 데이터는 남는다)
	@$(COMPOSE) down

restart: ## 내렸다 다시 띄우기
	@$(MAKE) --no-print-directory down
	@$(MAKE) --no-print-directory up

status: ## 셋 다 살아있는지 한눈에 확인
	@printf '%-12s %-10s %-10s %s\n' SERVICE STATE HEALTH PORTS
	@printf '%.0s-' $$(seq 1 52); printf '\n'
	@for s in $(SERVICES); do \
		cid=$$($(COMPOSE) ps -q $$s 2>/dev/null); \
		if [ -z "$$cid" ]; then \
			printf '%-12s %-10s %-10s %s\n' "$$s" "down" "-" "-"; \
		else \
			state=$$(docker inspect -f '{{.State.Status}}' $$cid); \
			health=$$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $$cid); \
			ports=$$(docker inspect -f '{{range $$p, $$b := .NetworkSettings.Ports}}{{range $$b}}{{.HostPort}} {{end}}{{end}}' $$cid); \
			printf '%-12s %-10s %-10s %s\n' "$$s" "$$state" "$$health" "$$ports"; \
		fi; \
	done

ps: ## 컴포즈 기본 상태 출력
	@$(COMPOSE) ps

logs: ## 인프라 로그 따라가기 (S=redis 로 특정 서비스만)
	@$(COMPOSE) logs -f $(S)

test: ## 전체 테스트 + 커버리지 리포트
	@./gradlew test jacocoTestReport

test-only: ## 특정 테스트만 실행 (T=com.aisip.OnO.backend.tag.*)
	@./gradlew test --tests "$(T)"

coverage: ## 커버리지 리포트를 브라우저로 열기
	@open build/reports/jacoco/test/html/index.html
