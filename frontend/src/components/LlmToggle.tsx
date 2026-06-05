import { useEffect, useState, useSyncExternalStore } from 'react';
import { api } from '../api/client';
import { getLlmProvider, setLlmProvider, subscribeLlmProvider } from '../lib/llmProvider';

/**
 * Sidebar toggle for the answering model, app-wide and persisted. OFF = local Ollama (free,
 * default); ON = OpenAI gpt-4o-mini. It drives the Researcher chat and the Analyst summaries.
 * If the backend reports OpenAI as unavailable (no OPENAI_API_KEY) the switch is disabled and any
 * stale 'openai' choice snaps back to Ollama, so the UI can never sit in an unusable state.
 */
export function LlmToggle() {
  const provider = useSyncExternalStore(subscribeLlmProvider, getLlmProvider, getLlmProvider);
  const [openAiAvailable, setOpenAiAvailable] = useState<boolean | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .llmProviders()
      .then((p) => alive && setOpenAiAvailable(!!p.openai))
      .catch(() => alive && setOpenAiAvailable(false));
    return () => {
      alive = false;
    };
  }, []);

  const isOpenAi = provider === 'openai';
  const disabled = openAiAvailable === false;

  // Never leave the app pointing at an unavailable provider.
  useEffect(() => {
    if (disabled && isOpenAi) setLlmProvider('ollama');
  }, [disabled, isOpenAi]);

  const title = disabled
    ? 'OpenAI is unavailable — set OPENAI_API_KEY to enable'
    : `Answering with ${isOpenAi ? 'OpenAI gpt-4o-mini' : 'local Ollama'} — click to switch`;

  return (
    <div className="llm-toggle" title={title}>
      <div className="llm-toggle-head">
        <span className="llm-toggle-cap">Answering model</span>
        <span className={`llm-badge ${isOpenAi ? 'openai' : 'ollama'}`}>
          {isOpenAi ? 'OpenAI' : 'Ollama'}
        </span>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={isOpenAi}
        aria-label="Answering model: off is local Ollama, on is OpenAI gpt-4o-mini"
        className={`llm-switch ${isOpenAi ? 'on' : ''}`}
        disabled={disabled}
        onClick={() => setLlmProvider(isOpenAi ? 'ollama' : 'openai')}
      >
        <span className="llm-switch-opt left">Ollama</span>
        <span className="llm-switch-opt right">OpenAI</span>
        <span className="llm-switch-knob" aria-hidden="true" />
      </button>
      {disabled && <div className="llm-toggle-note">Set OPENAI_API_KEY to enable</div>}
    </div>
  );
}
