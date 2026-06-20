# LinkedIn post — CryptoCopilot

*Ready to copy-paste. Everything below the line is the post (~200 words).*

---

I just shipped **CryptoCopilot** — less "a crypto bot," more a study in **production GenAI
engineering**, with crypto market data as the test bed rather than the point.

The core is a retrieval-augmented generation (RAG) pipeline on **Spring AI 1.0.8 + pgvector**: a
corpus indexer, a recency-aware retriever, and a generator that answers *only* from indexed sources,
cites every claim, and refuses anything out-of-corpus instead of guessing. Around it: explainable ML
(calibrated XGBoost + SHAP) and an LLM provider you can switch between local Ollama and OpenAI at
runtime — no code changes.

The metrics are deliberately honest, not inflated:

• RAG recall@8 = 0.90, with a 100% citation rate
• ML macro ROC-AUC 0.578 vs. 0.50 random — a real, data-limited edge, not a moonshot
• 231k+ rows of market, news & on-chain data behind it

One architecture idea I'm happy with: it's polyglot. Python does the ML, Java/Spring Boot runs the
app, React is the frontend — and the two languages never call each other. They share one
Postgres/pgvector database. No RPC, no shared model files.

Educational / analytics demo — not financial advice. Paper trading only.

Code: https://github.com/hzajkani/cryptocopilot

Happy to talk shop on RAG grounding, probability calibration, or polyglot boundaries — feedback welcome.

#GenAI #RAG #SpringAI #MachineLearning #SoftwareEngineering #pgvector
