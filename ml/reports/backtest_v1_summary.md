# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16800**
- Overall macro F1: **0.374**
- Top-1 accuracy: **0.417**
- Hit-rate when P(UP) > 0.5: **0.264**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.357 | 0.367 | 0.224 |
| AVAX | 0.309 | 0.315 | 0.287 |
| BNB | 0.373 | 0.580 | 0.286 |
| BTC | 0.334 | 0.634 | 0.308 |
| DOT | 0.325 | 0.339 | 0.293 |
| ETH | 0.371 | 0.434 | 0.234 |
| LINK | 0.318 | 0.332 | 0.265 |
| MATIC | 0.353 | 0.363 | 0.271 |
| SOL | 0.355 | 0.375 | 0.230 |
| XRP | 0.341 | 0.433 | 0.290 |

Best: **BNB** (0.373) · Worst: **AVAX** (0.309).
