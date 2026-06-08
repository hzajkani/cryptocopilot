# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16740**
- Overall macro F1: **0.375**
- Top-1 accuracy: **0.433**
- Hit-rate when P(UP) > 0.5: **0.265**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.361 | 0.376 | 0.240 |
| AVAX | 0.316 | 0.323 | 0.293 |
| BNB | 0.371 | 0.607 | 0.275 |
| BTC | 0.330 | 0.639 | 0.280 |
| DOT | 0.333 | 0.341 | 0.287 |
| ETH | 0.385 | 0.468 | 0.226 |
| LINK | 0.321 | 0.339 | 0.275 |
| MATIC | 0.376 | 0.392 | 0.283 |
| SOL | 0.350 | 0.388 | 0.210 |
| XRP | 0.344 | 0.456 | 0.300 |

Best: **ETH** (0.385) · Worst: **AVAX** (0.316).
