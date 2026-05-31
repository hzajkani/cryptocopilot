# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16290**
- Overall macro F1: **0.375**
- Top-1 accuracy: **0.430**
- Hit-rate when P(UP) > 0.5: **0.349**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.356 | 0.372 | 0.321 |
| AVAX | 0.295 | 0.304 | 0.480 |
| BNB | 0.350 | 0.595 | 0.368 |
| BTC | 0.340 | 0.661 | 0.333 |
| DOT | 0.342 | 0.355 | 0.328 |
| ETH | 0.373 | 0.455 | 0.361 |
| LINK | 0.314 | 0.330 | 0.420 |
| MATIC | 0.377 | 0.396 | 0.292 |
| SOL | 0.346 | 0.384 | 0.300 |
| XRP | 0.343 | 0.449 | 0.325 |

Best: **MATIC** (0.377) · Worst: **AVAX** (0.295).
