# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16360**
- Overall macro F1: **0.375**
- Top-1 accuracy: **0.433**
- Hit-rate when P(UP) > 0.5: **0.273**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.364 | 0.381 | 0.283 |
| AVAX | 0.312 | 0.317 | 0.263 |
| BNB | 0.345 | 0.593 | 0.294 |
| BTC | 0.345 | 0.664 | 0.269 |
| DOT | 0.343 | 0.353 | 0.269 |
| ETH | 0.362 | 0.447 | 0.278 |
| LINK | 0.310 | 0.324 | 0.279 |
| MATIC | 0.379 | 0.401 | 0.266 |
| SOL | 0.356 | 0.397 | 0.271 |
| XRP | 0.340 | 0.449 | 0.278 |

Best: **MATIC** (0.379) · Worst: **LINK** (0.310).
