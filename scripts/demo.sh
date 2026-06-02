#!/usr/bin/env bash
# CryptoCopilot — one-command demo: clone -> up -> a populated app.
#
# Brings up db + backend + frontend, ingests the five public data sources, trains the
# calibrated XGBoost model, writes predictions + SHAP drivers, builds the RAG index, and seeds
# a few illustrative paper trades — so Markets, Signals, Analyst, Chat, Paper Trades and
# Performance are all non-empty on first look.
#
# Honest scope (PROJECT.md §9): ML macro F1 ≈ 0.375 / AUC 0.578, and the default backtest makes
# 0 trades (single-snapshot ML) — these are deliberate, documented results, not bugs.
#
# Ollama is OPTIONAL. With it up, chat gives cited answers and the Analyst summary is LLM-phrased.
# With it down, chat refuses cleanly and the Analyst uses deterministic template summaries — the
# app still fully populates. The RAG index step below is skipped (not fatal) if Ollama is absent.
#
# One-time cost: the ingest + train steps take a few minutes (network crawl + model fit).
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

echo "==> 0/6  Preflight: .env"
if [ ! -f .env ]; then
  cp -n .env.example .env
  echo "    Created .env from .env.example. Add your free CoinGecko + Etherscan keys to .env,"
  echo "    then re-run 'make demo'. (Ingestion needs COINGECKO_API_KEY for market/fundamentals.)"
  exit 1
fi

echo "==> 1/6  Build + start db, backend, frontend (waits for healthchecks)…"
docker compose up -d --build --wait db backend frontend

echo "==> 2/6  Ingest all five public sources into Postgres (~minutes, network crawl)…"
docker compose run --rm ml python -m ml.ingest.run_all

echo "==> 3/6  Train the calibrated XGBoost model -> ml/models/v1/ (~1 min)…"
docker compose run --rm ml python -m ml.train

echo "==> 4/6  Write the latest predictions + SHAP drivers to Postgres…"
docker compose run --rm ml python -m ml.predict

echo "==> 5/6  Build the pgvector RAG index (needs a local Ollama for embeddings)…"
if curl -fsS -X POST http://localhost:8080/api/rag/reindex >/dev/null 2>&1; then
  echo "    RAG index built."
else
  echo "    Skipped: local Ollama not reachable. Chat will refuse until you start Ollama"
  echo "    (docs/OLLAMA_SETUP.md) and run:  make reindex"
fi

echo "==> 6/6  Seed a few illustrative paper trades…"
bash scripts/seed_demo_trades.sh

cat <<'EOF'

✅ Demo ready.
   Frontend : http://localhost:3000
   API docs : http://localhost:8080/swagger-ui.html
   Health   : http://localhost:8080/actuator/health

Honest scope (PROJECT.md §9):
  • ML macro F1 ≈ 0.375 / AUC 0.578 — a deliberate, documented data-limited result.
  • The default backtest makes 0 trades (single-snapshot ML); the TA proxy is negative. By design.
  • Chat + Analyst summaries use a local Ollama when present (docs/OLLAMA_SETUP.md); with it down,
    chat refuses and the Analyst falls back to deterministic templates — the app still populates.
EOF
