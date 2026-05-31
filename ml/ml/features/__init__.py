"""Feature engineering for the CryptoCopilot ML service (Stage 2).

Everything here is Python-internal: features are cached as parquet on a mounted
volume and **never** written to the database (PROJECT.md §3). Only the model's
``predictions``/``prediction_drivers`` cross the polyglot boundary.
"""
