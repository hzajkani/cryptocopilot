# Stage 3 — Spring Boot foundation + REST over the data + TA verdict (ta4j)

> **Phase B of 3 (Java/Spring backend).** This is Stage 3 of 7 — the first Java stage.
>
> **How to use this file:** New Claude Code session in the project root. First message: *"Read PROJECT.md and STATE.md before anything else."* Then paste everything below the line.

---

# CryptoCopilot — Stage 3: the Spring Boot application service (foundation + data API + TA verdict)

Read `PROJECT.md` and `STATE.md`. **Phase A is complete and verified** — Postgres holds the full 11-table contract schema, ~2 years of OHLCV, news/onchain/fundamentals, and the `ml` container writes `predictions` + `prediction_drivers`.

This stage stands up the **`backend` container** (Java 21 + Spring Boot 3.x + Maven), wires it to Postgres **read-only over Python's tables**, exposes a REST API over the existing data, and builds the **deterministic TA verdict engine with ta4j**. No RAG yet (Stage 4), no trading/Analyst yet (Stage 5), no frontend yet (Stage 6).

## Reality from Phase A — build to THIS, not to assumptions

- The DB schema is exactly `PROJECT.md` §5 (verified). Map JPA entities 1:1. `TIMESTAMPTZ` → `java.time.Instant`; `DOUBLE PRECISION` → `Double`.
- `predictions` rows: `timeframe = "4h"`, `model_version = "v1"`, `pred_class ∈ {UP, DOWN, FLAT}`, plus calibrated `prob_up/prob_down/prob_flat`.
- **`pred_class` is a validation-tuned weighted-argmax label — it is NOT `argmax(prob_*)`. Trust `pred_class` as the ML label. Define ML confidence = the calibrated probability of the predicted class** (`prob_up` if `pred_class=UP`, etc.). Never recompute the class from the probabilities.
- `prediction_drivers`: 3 rows per coin (`rank` 1..3), market-state feature names (the coin one-hot is deliberately excluded), with `shap_value`.
- Symbol `MATIC` in the DB is MATIC+POL stitched. Treat it as one symbol `MATIC`.

## Tasks

### 1. Maven project + container

`backend/` Spring Boot 3.x (Java 21), Maven. Starters: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `org.postgresql:postgresql`. TA: `org.ta4j:ta4j-core`. (Do NOT add Spring AI yet — Stage 4.) Tests: `spring-boot-starter-test`.

`backend/Dockerfile`: multi-stage — `maven:3.9-eclipse-temurin-21` to build, `eclipse-temurin:21-jre` to run, expose 8080.

`backend/src/main/resources/application.yml`:
- Datasource from env: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- **`spring.jpa.hibernate.ddl-auto: validate`** — `init.sql` owns the schema; Hibernate must never create or alter it. (If `validate` fights a type mapping, fall back to `none`, but prefer `validate` — it catches entity/table drift.)
- Actuator: expose `health`.

### 2. Add the `backend` service to `docker-compose.yml`

```yaml
  backend:
    build: ./backend
    env_file: .env
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/cryptocopilot
      SPRING_DATASOURCE_USERNAME: cc
      SPRING_DATASOURCE_PASSWORD: ccpass
      OPENAI_API_KEY: ${OPENAI_API_KEY}     # unused until Stage 4, harmless now
    depends_on:
      db:
        condition: service_healthy
    ports: ["8080:8080"]
```

### 3. JPA entities (read-only over Python-owned tables)

In `com.cryptocopilot.data`. Composite PKs via `@IdClass` or `@Embeddable`. Entities: `Ohlcv`, `MarketMeta`, `News`, `Onchain`, `Fundamentals`, `Prediction`, `PredictionDriver`. These mirror the contract columns exactly. They are **read-only** — the backend never writes to them (PROJECT.md §3).

### 4. Repositories

Spring Data JPA repositories with the queries the API needs:
- latest OHLCV row per symbol (per timeframe);
- OHLCV range: `findBySymbolAndTimeframeAndTsUtcBetweenOrderByTsUtc`;
- latest `predictions` per symbol (one "current" forecast each — the table holds the latest per `(ts,symbol,timeframe)`);
- the 3 `prediction_drivers` for a `(symbol, timeframe)` ordered by rank;
- latest `market_meta` per symbol;
- news for a symbol within N days, ordered by recency (`currencies LIKE %SYM%`).

### 5. DTOs + REST controllers

`com.cryptocopilot.api`. Return clean DTOs (not entities).

- **`GET /api/markets`** → 10 rows: `{symbol, price, change24hPct, marketCapUsd}` (price + 24h change from the latest 1h or 4h OHLCV; market cap from latest `market_meta`).
- **`GET /api/coins/{symbol}/ohlcv?timeframe=4h&from=&to=`** → candle array for charting `{ts, open, high, low, close, volume}`. Default range = last 90 days if not given.
- **`GET /api/signals`** → 10 rows, each `{symbol, ts, mlClass, mlConfidence, probUp, probDown, probFlat, modelVersion, drivers:[{rank,featureName,featureValue,shapValue}], ta:TAVerdict}`. `mlConfidence` = calibrated prob of `mlClass`.
- **`GET /api/ta/{symbol}`** → the full `TAVerdict` for one coin.
- **`GET /actuator/health`** → up.

### 6. The TA verdict engine (the headline of this stage) — `com.cryptocopilot.ta`

Port the deterministic Ichimoku-centric verdict (PROJECT.md §7) to Java using **ta4j** for the indicators.

- Load a coin's `4h` OHLCV from the DB into a ta4j `BarSeries`.
- Compute via ta4j: Ichimoku (`IchimokuTenkanSenIndicator`, `IchimokuKijunSenIndicator`, `IchimokuSenkouSpanAIndicator`, `IchimokuSenkouSpanBIndicator`, `IchimokuChikouSpanIndicator`), `RSIIndicator(14)`, `MACDIndicator(12,26)` + signal(9) + histogram, Bollinger Bands(20,2) → `bbPct`.
- Derive the same boolean flags as the spec: above/below/in cloud, TK cross bull/bear (today), chikou clear, cloud thickness sign.
- **Scoring (identical to the spec):** above cloud +2 / below cloud −2; TK cross bull +1 / bear −1; chikou clear +1; cloud thickness >0 +0.5 / <0 −0.5; MACD histogram >0 & rising +1 / <0 & falling −1; RSI>70 −1 / RSI<30 +1; bbPct>0.95 −0.5 / bbPct<0.05 +0.5. Sum → `score`.
- `direction`: `score>=+2` BULLISH, `score<=-2` BEARISH, else NEUTRAL. `confidence`: `|score|>=3` STRONG, `==2` MODERATE, else WEAK. `signals`: human-readable list of every non-zero contributing rule.

DTO:
```java
record TAVerdict(String symbol, Instant tsUtc, String direction,
                 String confidence, List<String> signals, double score) {}
```

This is independent of the ML model and deliberately recomputes Ichimoku in Java (PROJECT.md §3 — the small duplication keeps the polyglot boundary clean). Do NOT read any "feature" data from Python — compute from raw `ohlcv`.

### 7. Tests

- `TaVerdictTest` — golden test: feed a hand-built known-bullish bar series, assert BULLISH + the expected signals (mirror the capstone's `test_ta_verdict`).
- `SignalsControllerTest` (`@WebMvcTest` with a mocked service) — `/api/signals` returns 10 coins, each with `mlClass`, `mlConfidence`, and a `ta` block.
- A repository slice test (`@DataJpaTest`, or Testcontainers Postgres if you prefer) confirming OHLCV range and latest-prediction queries.

### 8. STATE.md + Git

Append a **Stage 3** section to `STATE.md`: endpoints live, ta4j version, TA-verdict sample for one coin, `ddl-auto: validate` result (entities match schema). Commit `"Stage 3: Spring Boot foundation + data REST API + ta4j TA verdict"`, tag `stage-3-done`.

## Definition of done

- `docker compose up -d` brings up `db`, `ml`, `backend`; `GET /actuator/health` is UP.
- `ddl-auto: validate` passes (entities match the real schema) — or documented why `none` was used.
- `GET /api/markets` → 10 coins with price + 24h change + market cap.
- `GET /api/signals` → 10 coins, each with ML class + confidence (= prob of predicted class) + top-3 drivers + a TA verdict.
- `GET /api/coins/BTC/ohlcv?timeframe=4h` → a non-empty candle array.
- All tests pass, including the TA golden test.

## What NOT to do

- Do NOT let Hibernate create/alter tables — `init.sql` is the schema source of truth.
- Do NOT recompute `pred_class` from the probabilities — trust the stored label.
- Do NOT read Python features; recompute indicators from `ohlcv` with ta4j.
- Do NOT write to any Python-owned table.
- Do NOT add Spring AI / RAG (Stage 4), trading or Analyst (Stage 5), or the frontend (Stage 6).
- Do NOT split the backend into multiple services — one modular monolith (PROJECT.md §2).
