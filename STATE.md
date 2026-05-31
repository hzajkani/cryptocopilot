# STATE — CryptoCopilot

> Living handoff between stages. Each stage reads `PROJECT.md` (frozen spec) then this file.

## Current status

**Stage 1 — Infrastructure + Postgres schema + all data ingestion: ✅ COMPLETE**
(tagged `stage-1-done`)

- Phase A of 3 (data + ML core). Containers live this stage: `db`, `ml`.
- `backend` (Stage 3) and `frontend` (Stage 6) are placeholders only.
- Next: **Stage 2** — ML service (features + XGBoost + calibration + SHAP → `predictions` + `prediction_drivers`).

## What is done

- **Monorepo** scaffold: `ml/`, `backend/.gitkeep`, `frontend/.gitkeep`, `db/init.sql`, `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`.
- **Postgres 16 + pgvector** container; full schema from `PROJECT.md` §5 created on first boot from `db/init.sql`. `\dt` shows all 11 contract tables; `\dx` shows `vector` 0.8.2. (No `vector_store` table — Stage 4 / Spring AI owns it.)
- **Python `ml` service** (Python 3.11): SQLAlchemy engine + idempotent `ON CONFLICT` upserts (`db.py`), five ingestion modules + `run_all` orchestrator, APScheduler daily-ingest stub (`scheduler.py`), one network smoke test (passes).
- **Full ingestion run** completed cleanly against a fresh DB with the project's API keys — every source populated; failures would log-and-skip, none were fatal.

## Concrete numbers (verified from the DB after a clean `run_all`)

Total rows ingested: **231,082**.

### `ohlcv` — 226,200 rows (10 coins × {1h, 4h, 1d}, ~2 years from Binance)

| coins | timeframe | bars each | date range |
|---|---|---|---|
| BTC ETH SOL BNB XRP ADA AVAX DOT LINK (9) | 1h | 17,520 | 2024-05-31 → 2026-05-31 |
| BTC ETH SOL BNB XRP ADA AVAX DOT LINK (9) | 4h | 4,380 | 2024-05-31 → 2026-05-31 |
| BTC ETH SOL BNB XRP ADA AVAX DOT LINK (9) | 1d | 730 | 2024-06-01 → 2026-05-31 |
| MATIC (merged MATIC/USDT + POL/USDT) | 1h / 4h / 1d | 17,441 / 4,361 / 728 | 2024-05-31 → 2026-05-31 |

MATIC/USDT went stale at the late-2024 POL rebrand; the loader stitches `MATIC/USDT` (pre-rebrand) and `POL/USDT` (post-rebrand) under the `MATIC` symbol, giving near-continuous 2-year history (a handful of bars missing across the transition — expected).

### `market_meta` — 3,660 rows (10 coins × 366 daily points)

Date range **2025-06-01 → 2026-05-31** (last ~365 days). CoinGecko's **Demo tier caps `market_chart` history at 365 days** (asking for 730 → HTTP 401), so market-cap history is 1 year while OHLCV history is 2 years. `circulating_supply` is derived as `market_cap / price`; `total_supply` is not in `market_chart` and is left NULL.

### `news` — 124 rows within the 180-day rolling window

| source | rows | POSITIVE | NEGATIVE | NEUTRAL |
|---|---|---|---|---|
| Decrypt | 39 | 16 | 12 | 11 |
| Cointelegraph | 30 | 9 | 9 | 12 |
| CoinDesk | 25 | 4 | 13 | 8 |
| The Block | 20 | 10 | 4 | 6 |
| Bitcoin Magazine | 10 | 5 | 2 | 3 |

51 of 124 items tagged with at least one currency (VADER sentiment on title+summary; rolling window prunes anything older than 180 days each run). Counts reflect each feed's current published window — re-running grows the table toward the 180-day cap.

### `onchain` — 1,088 rows

| symbol | source | metrics | rows | note |
|---|---|---|---|---|
| BTC | blockchain_com | `n-unique-addresses`, `n-transactions`, `estimated-transaction-volume-usd` | 1,084 | daily, last ~1 year |
| ETH | etherscan | `eth_supply`, `eth2_staking`, `burnt_fees`, `node_count` | 4 | point-in-time snapshot (one row per metric per run) |

### `fundamentals` — 10 rows (all 10 coins, point-in-time snapshot)

Market fields fully populated (`price_change_pct_24h/7d/30d`, `total_volume_usd`, `market_cap_change_pct_24h`). Social/developer coverage is sparse on the CoinGecko **Demo** tier (see skipped fields below).

## Skipped / partial (logged, non-fatal — per PROJECT.md §9 "log and skip")

- **CoinGecko `market_chart` history** capped at **365 days** on the Demo tier (730d returns 401). OHLCV history is still 2y (Binance).
- **`fundamentals.twitter_followers`** — NULL for all 10 coins (CoinGecko Demo no longer returns it).
- **`fundamentals.github_code_additions_4w` / `github_code_deletions_4w`** — missing for **AVAX, DOT, MATIC** (no developer code-stats from CoinGecko for those ids).
- **`fundamentals.reddit_subscribers`** present but returned as `0` by the Demo tier for these coins; **`github_commit_count_4w`** is `0` for several coins (ADA, AVAX, BNB, DOT, MATIC) and non-zero for BTC/ETH/LINK/SOL/XRP.
- **Etherscan** — only the free `stats` endpoints are used (`ethsupply`, `ethsupply2`, `nodecount`). Daily historical endpoints are Pro-only and are not attempted (would be logged-and-skipped).

## How to reproduce

```bash
cp .env.example .env            # add CoinGecko Demo + Etherscan free keys
docker compose up -d db
docker compose run --rm ml python -m ml.ingest.run_all
docker compose exec db psql -U cc -d cryptocopilot \
  -c "SELECT symbol, timeframe, count(*) FROM ohlcv GROUP BY 1,2 ORDER BY 1,2;"
```

The `ml` container's default command is the APScheduler daily-ingest stub (`python -m ml.scheduler`), which keeps the batch worker alive.

## Definition of done — checklist

- [x] `docker compose up -d db` → Postgres healthy; `\dt` shows all contract tables; `\dx` shows `vector`.
- [x] `docker compose run --rm ml python -m ml.ingest.run_all` populates all five Python-owned data tables without crashing.
- [x] `ohlcv` has ~2 years for all 10 coins (MATIC continuous via MATIC+POL merge).
- [x] `news` has rows from all 5 sources within the last 180 days.
- [x] `onchain` has BTC (blockchain_com) and ETH (etherscan) rows.
- [x] `fundamentals` has rows for all 10 coins CoinGecko returned.
- [x] Network smoke test passes (`pytest -q`).
