// App-wide LLM provider toggle (Researcher chat + Analyst summaries). Default is the free local
// Ollama; the user can switch to OpenAI gpt-4o-mini from the sidebar. The choice is persisted in
// localStorage and exposed as a tiny external store so React (useSyncExternalStore) and the plain
// api client (getLlmProvider) read the SAME source of truth — the client attaches it to every
// chat/analyst call, so toggling takes effect immediately everywhere without prop-drilling.
//
// Embeddings/retrieval are unaffected — only the model that writes the answer/summary changes.

export type LlmProviderId = 'ollama' | 'openai';

const KEY = 'cc.llmProvider';
const listeners = new Set<() => void>();

function read(): LlmProviderId {
  try {
    return localStorage.getItem(KEY) === 'openai' ? 'openai' : 'ollama';
  } catch {
    return 'ollama'; // localStorage unavailable (private mode / SSR) → safe default
  }
}

let current: LlmProviderId = read();

/** The active provider. Safe to call from non-React code (the api client uses this). */
export function getLlmProvider(): LlmProviderId {
  return current;
}

/** Switch provider, persist it, and notify subscribers. No-op if unchanged. */
export function setLlmProvider(next: LlmProviderId): void {
  if (next === current) return;
  current = next;
  try {
    localStorage.setItem(KEY, next);
  } catch {
    /* ignore quota / unavailable storage — in-memory value still updates */
  }
  listeners.forEach((l) => l());
}

/** Subscribe to changes (for React's useSyncExternalStore). Returns an unsubscribe fn. */
export function subscribeLlmProvider(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
