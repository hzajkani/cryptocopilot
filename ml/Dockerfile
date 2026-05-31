FROM python:3.11-slim

# build-essential is needed to compile a few wheels (e.g. some ccxt / numpy deps)
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install dependencies first (better layer caching): copy only the project
# metadata, then install. The package itself is copied in the next step.
# `.[dev]` adds pytest/ruff so the Stage 2 test suite runs in-container
# (`docker compose run --rm ml pytest`).
COPY pyproject.toml ./
RUN pip install --no-cache-dir ".[dev]"

# Copy the actual package source (and tests, so `pytest` works in-container).
COPY ml ./ml
COPY tests ./tests

# Default command matches docker-compose: the scheduler keeps the batch
# worker alive. Manual ingestion is `docker compose run --rm ml python -m ml.ingest.run_all`.
CMD ["python", "-m", "ml.scheduler"]
