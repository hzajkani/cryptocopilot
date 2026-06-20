# Stage 8 — README Repositioning + LinkedIn Post

## Context

Read `PROJECT.md` (frozen spec) and `STATE.md` (current status — Stage 7 / `v1.0` complete) before
starting anything. This stage is **non-functional**: no schema change, no new model, no new trading
behaviour, no architectural change. It only repositions documentation and adds one new content file.

## Why

This repo is a portfolio piece. Its real selling point is the **GenAI / RAG / Spring AI engineering**
— not "a crypto bot." The current README treats RAG as one module among several; it needs to read as
an **AI engineering project that happens to use crypto market data as its domain**, with crypto
positioned as the test bed, not the headline.

## Task 1 — Reposition `README.md`

1. **Rewrite the opening pitch** (first 1–2 paragraphs). Lead with the AI engineering story:
   production RAG built on Spring AI + pgvector, grounded/cited generation, a numeric hallucination
   guard, explainable ML (SHAP), an LLM provider that's swappable (local Ollama ↔ OpenAI) without code
   changes. State plainly that crypto market data is the chosen domain used to exercise this stack,
   not the product itself.

2. **Add (or substantially expand) a dedicated "GenAI / RAG engineering" section**, placed near the
   top, covering at minimum:
   - Spring AI 1.0.8 integration: indexer → retriever → generator pipeline
   - pgvector store (HNSW, cosine, 768-dim) — what's indexed (news / onchain / fundamental / KB) and
     the chunk counts
   - Grounding rules: strict `[N]` citation format, fixed refusal phrase on out-of-corpus questions,
     no ungrounded claims
   - The Analyst's hallucination guard: the LLM only phrases a summary, never invents numbers; the
     guard validates output against the deterministic input
   - Eval methodology and the real numbers: recall@8 = 0.90, citation rate 100%, retrieval breakdown
     by query type, classifier accuracy
   - The local/cloud LLM toggle (Ollama → OpenAI `gpt-4o-mini` + `text-embedding-3-small`) and why
     both paths are documented

3. **Screenshots / demo.** Run `make demo` against a fresh stack and capture real PNGs for Markets,
   Signals, Analyst, Researcher chat (a cited answer — this is the one that matters most), and
   Performance. Replace the current placeholders in `docs/img/` with the real screenshots and confirm
   the README renders them. If a short (10–20s) screen recording or GIF of the Researcher chat
   returning a cited answer is feasible in this environment, add it near the GenAI section; if not,
   leave a one-line instruction for where to drop it later (`docs/img/chat-demo.gif`) and reference it
   in the README so it's a five-minute manual step, not a missing piece.

4. **Disclaimer.** Make sure this exact phrase appears prominently, near the top (under the title,
   before the architecture section):

   > **Educational / analytics demo — not financial advice.**

   Keep the existing "paper trading only" framing alongside it. Don't remove the current disclaimer
   language — just make sure this exact phrasing is present verbatim, not a paraphrase of it.

5. Keep the rest of the README's structure (architecture diagram, honest-scope table, data sources,
   table ownership, build story, tests, repo layout) — it all stays, just demoted below the new
   GenAI-first opening and section.

## Task 2 — Remove personal/career framing, repo-wide

Grep `README.md`, every file under `docs/`, and `STATE.md` (do **not** touch `PROJECT.md`, it's
frozen) plus any code comments for personal or career-targeting language: job-search intent, a
named target city or market (e.g. Frankfurt, "fintech market" framing), recruiter-facing phrasing,
bootcamp/instructor names, a "career transition" narrative. Remove or neutralize anything found — the
project should read as a standalone technical portfolio piece, not a job application. Keep the tone
professional and a little understated; don't oversell. If you find nothing, say so explicitly in your
summary at the end — don't silently skip the check.

## Task 3 — LinkedIn post, as a new file

Create `docs/LINKEDIN_POST.md`: a single ready-to-publish LinkedIn post (~150–220 words, short
paragraphs, line breaks for readability, at most 1–2 emoji if any).

Content:
- Open with what was built and the engineering angle (RAG + Spring AI + pgvector + explainable ML),
  not "I built a crypto bot."
- 3–5 concrete, honest numbers pulled from `STATE.md` (e.g. ROC-AUC 0.578 vs. 0.50 random baseline,
  RAG recall@8 = 0.90, 100% citation rate, 231k OHLCV rows). The "honest metrics, not inflated"
  framing is itself part of the pitch — keep it.
- One line on the polyglot architecture (Python ML + Java/Spring Boot + React, one shared
  Postgres/pgvector boundary, no RPC).
- The disclaimer line: educational/analytics demo, not financial advice; paper trading only.
- A GitHub link placeholder: `https://github.com/hzajkani/cryptocopilot`
- 4–6 relevant hashtags (e.g. `#GenAI #RAG #SpringAI #MachineLearning #SoftwareEngineering`).
- **No** job-search call-to-action, no named target market or city, no "open to opportunities in X."
  End on something neutral — inviting technical feedback/questions, or just stop after the link and
  hashtags.

## Definition of done

- [ ] README opens with the AI-engineering pitch; the GenAI/RAG section reads as the centerpiece
- [ ] Real screenshots in `docs/img/`, README renders them (not placeholders) — or a clearly flagged
      manual step if blocked in this environment
- [ ] Exact phrase **"Educational / analytics demo — not financial advice."** present near the top
- [ ] No personal/career-targeting language anywhere in README/docs/STATE.md — explicitly confirmed
      in your summary
- [ ] `docs/LINKEDIN_POST.md` exists, ready to copy-paste, with no job-search framing
- [ ] `STATE.md` updated with a short Stage 8 entry (consistent with the existing per-stage pattern);
      no new git tag needed unless you think it's warranted
