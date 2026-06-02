#!/usr/bin/env bash
# Seed a handful of illustrative PAPER trades so the Paper Trades + Performance pages are
# non-empty for a first look. This is SEEDED DEMO ACTIVITY, not a strategy — no real money,
# ever (PROJECT.md §9). Re-runnable: it resets the paper account to 10,000 USD first.
#
# Usage: bash scripts/seed_demo_trades.sh   (override the API with BACKEND_URL=...)
set -euo pipefail

BASE="${BACKEND_URL:-http://localhost:8080}"
say() { printf '  %s\n' "$*"; }

# The backend may have just come up — wait for actuator health (max ~120s).
say "Waiting for backend at $BASE …"
for i in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" >/dev/null 2>&1; then break; fi
  if [ "$i" -eq 60 ]; then echo "backend not healthy after 120s" >&2; exit 1; fi
  sleep 2
done

# Place one paper order. Args: SYMBOL SIDE TYPE QUANTITY [LIMIT_PRICE].
order() {
  local symbol="$1" side="$2" type="$3" qty="$4" limit="${5:-null}"
  local body resp status
  body=$(printf '{"symbol":"%s","side":"%s","type":"%s","quantity":%s,"limitPrice":%s}' \
    "$symbol" "$side" "$type" "$qty" "$limit")
  resp=$(curl -fsS -X POST "$BASE/api/orders" -H 'Content-Type: application/json' -d "$body")
  status=$(printf '%s' "$resp" | grep -o '"status":"[A-Z]*"' | head -1 | cut -d'"' -f4)
  say "$side $qty $symbol $type  ->  ${status:-?}"
}

echo "Seeding illustrative paper trades (demo activity, paper only)…"
curl -fsS -X POST "$BASE/api/account/reset?startingBalance=10000" >/dev/null
say "Account reset to 10,000 USD"

# A few MARKET buys across the majors, then one closing SELL so the blotter and the
# Performance equity curve show a realized round-trip rather than a single flat point.
order BTC  BUY  MARKET 0.05
order ETH  BUY  MARKET 1.0
order SOL  BUY  MARKET 10
order LINK BUY  MARKET 50
order ETH  SELL MARKET 0.5

echo "Done — see Paper Trades (http://localhost:3000/trade) and Performance (/performance)."
