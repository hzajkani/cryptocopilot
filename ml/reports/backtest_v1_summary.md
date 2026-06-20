# Backtest — ML direction model `v1`

Calibrated 3-class (UP/DOWN/FLAT) forecast over the **test window** (out-of-sample, chronological). This is the honest record, not a live trading claim.

- Rows scored: **16840**
- Overall macro F1: **0.378**
- Top-1 accuracy: **0.438**
- Hit-rate when P(UP) > 0.5: **0.257**

## Per-symbol

| symbol | macro F1 | top-1 acc | UP hit-rate |
|---|---|---|---|
| ADA | 0.366 | 0.384 | 0.244 |
| AVAX | 0.317 | 0.328 | 0.257 |
| BNB | 0.377 | 0.603 | 0.308 |
| BTC | 0.346 | 0.656 | 0.304 |
| DOT | 0.338 | 0.351 | 0.258 |
| ETH | 0.374 | 0.460 | 0.256 |
| LINK | 0.322 | 0.344 | 0.227 |
| MATIC | 0.378 | 0.397 | 0.287 |
| SOL | 0.353 | 0.393 | 0.210 |
| XRP | 0.354 | 0.466 | 0.300 |

Best: **MATIC** (0.378) · Worst: **AVAX** (0.317).
