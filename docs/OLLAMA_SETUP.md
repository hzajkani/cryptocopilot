# Local LLM setup (Ollama) — for the Stage 4 RAG "Researcher"

CryptoCopilot's RAG chat (Stage 4) runs entirely on a **local [Ollama](https://ollama.com)** —
**free, no API key, offline**. Ollama serves *both* models the Researcher needs:

| role | model | size | notes |
|---|---|---|---|
| chat (writes the cited answer) | `llama3.2:3b` | ~2.0 GB | small, fast, instruction-following |
| embeddings (indexes + searches the corpus) | `nomic-embed-text` | ~274 MB | **768-dim** (must match `pgvector.dimensions`) |

> Why local? RAG needs an embedding model *and* a chat model. This avoids any paid API. You can
> switch to OpenAI later with a config flip (see [Switching providers](#switching-providers)).

This guide is the per-PC setup, since the project runs on several machines.

---

## 1. Install Ollama

**macOS**
```bash
brew install ollama
brew services start ollama        # runs the server now + at login (http://localhost:11434)
# (or, no background service:)  ollama serve
```

**Linux**
```bash
curl -fsSL https://ollama.com/install.sh | sh
# systemd usually starts it automatically; otherwise:  ollama serve &
```

**Windows**
- Download and run the installer from <https://ollama.com/download> (it starts a background service),
  **or** use WSL2 and follow the Linux steps.

Verify the server is up (all platforms):
```bash
curl http://localhost:11434/api/version      # -> {"version":"..."}
```

## 2. Pull the two models

```bash
ollama pull llama3.2:3b
ollama pull nomic-embed-text
ollama list                                   # both should be listed
```

Quick functional check:
```bash
# embeddings must report 768 dimensions
curl -s http://localhost:11434/api/embed \
  -d '{"model":"nomic-embed-text","input":"hello"}' | grep -o '\[' | wc -l   # many floats -> ok
# chat responds
curl -s http://localhost:11434/api/chat \
  -d '{"model":"llama3.2:3b","stream":false,"messages":[{"role":"user","content":"say OK"}]}'
```

## 3. How the backend connects

The Spring backend reads these (all have sensible defaults — see `backend/src/main/resources/application.yml`):

| variable | default | meaning |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | where Ollama listens |
| `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | chat model tag |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | embedding model tag |

Provider selection lives in `application.yml`: `spring.ai.model.chat=ollama`,
`spring.ai.model.embedding=ollama`, and `spring.ai.vectorstore.pgvector.dimensions=768`.

- **Running the backend in Docker** (`docker compose up -d backend`): Ollama runs on the **host**,
  so the container reaches it at `http://host.docker.internal:11434`. This is already wired in
  `docker-compose.yml` (env `OLLAMA_BASE_URL` + an `extra_hosts: host.docker.internal:host-gateway`
  mapping that makes it resolve on Linux too). **Ollama runs on the host, not in a container.**
- **Running the backend locally** (`mvn spring-boot:run` / tests): the default `localhost:11434`
  is correct — no env needed.

## 4. Use it

```bash
# 1. (Docker) start the stack; Ollama must already be running on the host
docker compose up -d db backend

# 2. build the RAG index (embeds the corpus via Ollama) and check counts
curl -s -X POST localhost:8080/api/rag/reindex     # -> {"news":...,"onchain":...,"fundamental":10,"kb":70}
curl -s localhost:8080/api/rag/status

# 3. ask a question (grounded, cited)
curl -s -X POST localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"query":"How does Solana achieve consensus?"}'

# 4. (optional) run the live integration test + retrieval eval (needs db + Ollama up)
cd backend && RAG_LIVE=1 mvn -Dtest=RagLiveIT test   # writes reports/retrieval_eval.md
```

The default `mvn test` (39 tests) does **not** need Ollama — the live `*IT` test is separate and
gated by `RAG_LIVE`.

## 5. Switching providers

To use OpenAI instead of Ollama (e.g. `gpt-4o-mini` + `text-embedding-3-small`):

1. In `application.yml` set `spring.ai.model.chat=openai`, `spring.ai.model.embedding=openai`,
   and `spring.ai.vectorstore.pgvector.dimensions=1536`.
2. Put a funded key in `.env` as `OPENAI_API_KEY=sk-...`.
3. The embedding dimension changed, so recreate the store and re-index:
   ```bash
   docker compose exec db psql -U cc -d cryptocopilot -c "DROP TABLE IF EXISTS vector_store;"
   docker compose up -d --force-recreate backend
   curl -s -X POST localhost:8080/api/rag/reindex
   ```
Both starters are already on the classpath, so no dependency change is needed.

## 6. Troubleshooting

- **`Connection refused` / chat hangs** — Ollama isn't running (`curl localhost:11434/api/version`),
  or (Docker) the container can't reach the host. Confirm from inside the container:
  `docker compose exec backend sh -c "curl -s http://host.docker.internal:11434/api/version"`.
- **`model "..." not found`** — you didn't `ollama pull` it (or set `OLLAMA_*_MODEL` to a tag you
  don't have). Run `ollama list`.
- **`expected N dimensions, not 768` (or vice-versa) on reindex** — the `vector_store` table was
  created for a different embedding model. Drop it and let the app recreate it:
  `docker compose exec db psql -U cc -d cryptocopilot -c "DROP TABLE IF EXISTS vector_store;"`,
  then restart the backend and re-index.
- **First chat is slow** — Ollama lazy-loads the model into memory on first use; subsequent calls
  are faster. `llama3.2:3b` runs comfortably on ~8 GB RAM.
- **Want better answers** — pull a larger model (e.g. `ollama pull qwen2.5:7b`) and set
  `OLLAMA_CHAT_MODEL=qwen2.5:7b`. The embedding model (and thus the index) is unchanged.
