# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16490**
- Overall macro F1: **0.383**
- Top-1 accuracy: **0.437**
- Hit-rate when P(UP) > 0.5: **0.301**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.359 | 0.374 | 0.271 |
| AVAX | 0.315 | 0.318 | 0.336 |
| BNB | 0.377 | 0.623 | 0.320 |
| BTC | 0.331 | 0.653 | 0.320 |
| DOT | 0.353 | 0.361 | 0.277 |
| ETH | 0.374 | 0.449 | 0.276 |
| LINK | 0.337 | 0.354 | 0.330 |
| MATIC | 0.378 | 0.397 | 0.297 |
| SOL | 0.350 | 0.384 | 0.264 |
| XRP | 0.350 | 0.459 | 0.362 |

Best: **MATIC** (0.378) · Worst: **AVAX** (0.315).
