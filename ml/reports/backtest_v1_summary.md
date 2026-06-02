# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16430**
- Overall macro F1: **0.379**
- Top-1 accuracy: **0.427**
- Hit-rate when P(UP) > 0.5: **0.270**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.360 | 0.374 | 0.233 |
| AVAX | 0.325 | 0.329 | 0.293 |
| BNB | 0.362 | 0.588 | 0.286 |
| BTC | 0.318 | 0.634 | 0.242 |
| DOT | 0.327 | 0.341 | 0.308 |
| ETH | 0.377 | 0.452 | 0.246 |
| LINK | 0.329 | 0.341 | 0.286 |
| MATIC | 0.353 | 0.370 | 0.240 |
| SOL | 0.364 | 0.392 | 0.250 |
| XRP | 0.354 | 0.454 | 0.327 |

Best: **ETH** (0.377) · Worst: **BTC** (0.318).
