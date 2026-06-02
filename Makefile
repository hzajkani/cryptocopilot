# CryptoCopilot — developer entry points. `make demo` is the one-command path
# (clone -> up -> a populated app). Run `make help` to list targets.
.DEFAULT_GOAL := help
SHELL := /bin/bash

.PHONY: help demo up down logs ingest train predict reindex seed test clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

demo: ## One-command demo: up + ingest + train + predict + reindex + seed (~minutes the first time)
	@bash scripts/demo.sh

up: ## Build + start db, backend, frontend (waits for healthchecks)
	docker compose up -d --build --wait db backend frontend

down: ## Stop all containers (keeps the pgdata volume)
	docker compose down

logs: ## Tail the backend + frontend logs
	docker compose logs -f backend frontend

ingest: ## Crawl all five public sources into Postgres (~minutes)
	docker compose run --rm ml python -m ml.ingest.run_all

train: ## Train the calibrated XGBoost model -> ml/models/v1/
	docker compose run --rm ml python -m ml.train

predict: ## Write the latest predictions + SHAP drivers to Postgres
	docker compose run --rm ml python -m ml.predict

reindex: ## Rebuild the pgvector RAG index (needs Ollama for embeddings)
	curl -fsS -X POST http://localhost:8080/api/rag/reindex && echo

seed: ## Seed a few illustrative paper trades (demo activity, paper only)
	@bash scripts/seed_demo_trades.sh

test: ## Run the offline test suites (ml pytest, backend mvn, frontend vitest)
	docker compose run --rm ml pytest -q -m "not network"
	cd backend && mvn -q test
	cd frontend && npm ci && npm run build && npm test

clean: ## Stop containers AND delete the pgdata volume (full reset)
	docker compose down -v
