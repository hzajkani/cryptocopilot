CREATE EXTENSION IF NOT EXISTS vector;

-- ===================== PYTHON-OWNED (data + ML service) =====================

CREATE TABLE ohlcv (
  ts_utc      TIMESTAMPTZ NOT NULL,
  symbol      TEXT        NOT NULL,
  timeframe   TEXT        NOT NULL,
  open        DOUBLE PRECISION,
  high        DOUBLE PRECISION,
  low         DOUBLE PRECISION,
  close       DOUBLE PRECISION,
  volume      DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol, timeframe)
);

CREATE TABLE market_meta (
  ts_utc              TIMESTAMPTZ NOT NULL,
  symbol              TEXT        NOT NULL,
  market_cap_usd      DOUBLE PRECISION,
  circulating_supply  DOUBLE PRECISION,
  total_supply        DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol)
);

CREATE TABLE news (
  id              TEXT PRIMARY KEY,           -- hash of url
  ts_utc          TIMESTAMPTZ NOT NULL,
  title           TEXT,
  summary         TEXT,
  source          TEXT,                       -- CoinDesk / Cointelegraph / ...
  url             TEXT,
  currencies      TEXT,                       -- CSV of tagged symbols
  sentiment       TEXT,                       -- POSITIVE / NEGATIVE / NEUTRAL
  sentiment_score DOUBLE PRECISION
);
CREATE INDEX idx_news_ts ON news (ts_utc);

CREATE TABLE onchain (
  ts_utc  TIMESTAMPTZ NOT NULL,
  symbol  TEXT        NOT NULL,
  metric  TEXT        NOT NULL,
  value   DOUBLE PRECISION,
  source  TEXT,                               -- blockchain_com / etherscan
  PRIMARY KEY (ts_utc, symbol, metric)
);

CREATE TABLE fundamentals (
  ts_utc                    TIMESTAMPTZ NOT NULL,
  symbol                    TEXT        NOT NULL,
  price_change_pct_24h      DOUBLE PRECISION,
  price_change_pct_7d       DOUBLE PRECISION,
  price_change_pct_30d      DOUBLE PRECISION,
  total_volume_usd          DOUBLE PRECISION,
  market_cap_change_pct_24h DOUBLE PRECISION,
  reddit_subscribers        INTEGER,
  reddit_active_48h         INTEGER,
  reddit_avg_posts_48h      DOUBLE PRECISION,
  twitter_followers         INTEGER,
  github_commit_count_4w    INTEGER,
  github_prs_merged         INTEGER,
  github_code_additions_4w  INTEGER,
  github_code_deletions_4w  INTEGER,
  PRIMARY KEY (ts_utc, symbol)
);

CREATE TABLE predictions (
  ts_utc        TIMESTAMPTZ NOT NULL,
  symbol        TEXT        NOT NULL,
  timeframe     TEXT        NOT NULL,
  pred_class    TEXT,                          -- UP / DOWN / FLAT
  prob_up       DOUBLE PRECISION,
  prob_down     DOUBLE PRECISION,
  prob_flat     DOUBLE PRECISION,
  model_version TEXT,
  created_at    TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (ts_utc, symbol, timeframe)
);

CREATE TABLE prediction_drivers (
  ts_utc        TIMESTAMPTZ NOT NULL,
  symbol        TEXT        NOT NULL,
  timeframe     TEXT        NOT NULL,
  rank          INTEGER     NOT NULL,          -- 1..3 (top SHAP drivers)
  feature_name  TEXT,
  feature_value DOUBLE PRECISION,
  shap_value    DOUBLE PRECISION,
  PRIMARY KEY (ts_utc, symbol, timeframe, rank)
);

-- ===================== JAVA-OWNED (application + API service) ================

CREATE TABLE account_state (
  ts_utc           TIMESTAMPTZ PRIMARY KEY,
  cash_usd         DOUBLE PRECISION,
  total_equity_usd DOUBLE PRECISION
);

CREATE TABLE positions (
  symbol          TEXT PRIMARY KEY,
  size            DOUBLE PRECISION,
  avg_entry_price DOUBLE PRECISION,
  opened_at       TIMESTAMPTZ
);

CREATE TABLE trades (
  id           TEXT PRIMARY KEY,
  ts_utc       TIMESTAMPTZ,
  symbol       TEXT,
  side         TEXT,                           -- BUY / SELL
  quantity     DOUBLE PRECISION,
  price        DOUBLE PRECISION,
  fees         DOUBLE PRECISION,
  realized_pnl DOUBLE PRECISION,
  notes        TEXT
);

CREATE TABLE orders (
  id           TEXT PRIMARY KEY,
  ts_submitted TIMESTAMPTZ,
  ts_filled    TIMESTAMPTZ,
  symbol       TEXT,
  side         TEXT,                           -- BUY / SELL
  type         TEXT,                           -- MARKET / LIMIT
  quantity     DOUBLE PRECISION,
  limit_price  DOUBLE PRECISION,
  status       TEXT,                           -- PENDING / FILLED / CANCELLED
  filled_price DOUBLE PRECISION,
  fees         DOUBLE PRECISION
);

-- Spring AI manages its own pgvector table (default name: vector_store),
-- created automatically when spring.ai.vectorstore.pgvector.initialize-schema=true.
-- Do NOT create it by hand here.
