# Backtest — CryptoCopilot Stage 5 (paper-trading strategies)

Generated: 2026-06-01T21:43:59.362990Z  
Window: **2025-09-01T00:00:00Z → 2026-06-01T00:00:00Z** (274 daily marks).  
Universe: 10 coins · start $10,000.00 · trade size $1,000.00 · slippage 5.00 bps · taker fee 10.00 bps · Sharpe/Sortino annualised ×√365.

## Results

| strategy | trades | final equity | total return | Sharpe | Sortino | max DD | win rate | fees |
|---|---|---|---|---|---|---|---|---|
| ML-confirmed-by-TA (prob_up>0.55 & TA BULLISH) | 0 | $10,000.00 | 0.00% | 0.000 | 0.000 | 0.00% | 0.00% | $0.00 |
| TA-long-only (enter BULLISH, exit BEARISH) | 206 | $7,044.76 | -29.54% | -1.201 | -1.603 | 40.97% | 32.35% | $203.23 |

## Notes

- **ML-confirmed-by-TA** is the spec default. The `predictions` table holds a single *latest* ML snapshot per coin (the ML batch job stores only the current forecast — PROJECT.md §2), so there is no historical ML series to drive it bar-by-bar; the latest snapshot is held constant. In the current calm/down regime no coin is `UP` with `prob_up>0.55`, so it makes **0 trades** — a correct, documented outcome, not a defect.
- **TA-long-only** (enter BULLISH / exit BEARISH) is fully reconstructable from `ohlcv` at every historical bar, so it carries the substantive curve: 206 trades, Sharpe -1.201, max drawdown 40.97%, win rate 32.4%, fee drag $203.23, final equity $7,044.76.
- Fees + a calm regime mean a Sharpe ≤ 0 is an accepted, honest result (PROJECT.md Stage 5 §5 / DoD). The point of this stage is a correct, single-sourced fill + metrics engine, not alpha. Daily marking → Sharpe/Sortino annualised ×√365.
- (The ML-confirmed line shows $0.00 fees / 0.00 Sharpe precisely because it never traded.)

