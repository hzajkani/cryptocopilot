import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { errMessage } from '../lib/useAsync';
import { useToast } from '../components/Toast';
import { PageHead } from '../components/ui';
import { CitationSourceBadge, SourceBadge } from '../components/SourceBadge';
import { IconExternal } from '../components/icons';
import { UNIVERSE, type AnswerWithCitations, type Citation } from '../api/types';

// The backend's exact deterministic refusal phrase (Stage 4) — shown when the sources
// don't cover the question. Rendered as a calm system message, never an error (Stage 6 brief).
const REFUSALS = ['The available sources do not answer this question.'];

export function isRefusalOrEmpty(answer: string): boolean {
  const a = answer.trim();
  return a.length === 0 || REFUSALS.includes(a);
}

// One-tap example prompts for the empty Researcher: a mix of mechanism, news and on-chain
// questions plus the now-supported signal/advice style.
const SAMPLE_QUESTIONS = [
  'How does Solana achieve consensus?',
  "What's the latest news on Bitcoin?",
  'Is now a good time to add to ETH?',
  'Do the signals lean bullish or bearish on SOL?',
  'What are the biggest risks for BTC right now?',
];

type ChatMsg =
  | { role: 'user'; text: string }
  | { role: 'system'; text: string }
  | { role: 'bot'; answer: AnswerWithCitations };

/** Render an answer string, turning [N] markers into clickable citation chips. */
function AnswerBody({ answer, citations }: { answer: string; citations: Citation[] }) {
  const parts = answer.split(/(\[\d+\])/g);
  return (
    <>
      {parts.map((part, i) => {
        const m = part.match(/^\[(\d+)\]$/);
        if (!m) return <span key={i}>{part}</span>;
        const num = Number(m[1]);
        const cite = citations.find((c) => c.number === num);
        const title = cite
          ? `${cite.source ?? cite.sourceType}${cite.symbol ? ` · ${cite.symbol}` : ''}: ${cite.snippet}`
          : `Citation ${num}`;
        return (
          <span
            key={i}
            className="cite-chip"
            title={title}
            onClick={() => cite?.url && window.open(cite.url, '_blank', 'noopener')}
          >
            {num}
          </span>
        );
      })}
    </>
  );
}

function BotMessage({ answer }: { answer: AnswerWithCitations }) {
  return (
    <div className="msg bot">
      <AnswerBody answer={answer.answer} citations={answer.citations} />
      {answer.citations.length > 0 && (
        <div className="citations">
          {answer.citations.map((c) => (
            <div className="citation" key={c.number}>
              <span className="cnum">[{c.number}]</span>
              <span>
                <CitationSourceBadge citation={c} />{' '}
                <strong>{c.source ?? c.sourceType}</strong>
                {c.symbol ? <span className="dim"> · {c.symbol}</span> : null} — {c.snippet}
                {c.url && (
                  <>
                    {' '}
                    <a href={c.url} target="_blank" rel="noopener noreferrer">
                      <IconExternal />
                    </a>
                  </>
                )}
              </span>
            </div>
          ))}
        </div>
      )}
      <div className="chat-meta">
        {answer.provider === 'openai' ? 'OpenAI gpt-4o-mini' : 'Ollama'} ·{' '}
        {answer.queryClassification} · {answer.latencyMs} ms
      </div>
    </div>
  );
}

export function ChatPage() {
  const toast = useToast();
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState('');
  const [symbols, setSymbols] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = logRef.current;
    // scrollTo is unimplemented in jsdom (tests) — guard it.
    if (el && typeof el.scrollTo === 'function') {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    }
  }, [messages, busy]);

  function toggleSymbol(sym: string) {
    setSymbols((cur) => (cur.includes(sym) ? cur.filter((s) => s !== sym) : [...cur, sym]));
  }

  async function send(text?: string) {
    const query = (text ?? input).trim();
    if (!query || busy) return;
    setMessages((m) => [...m, { role: 'user', text: query }]);
    setInput('');
    setBusy(true);
    try {
      const answer = await api.chat({ query, symbols: symbols.length ? symbols : undefined });
      setMessages((m) =>
        isRefusalOrEmpty(answer.answer)
          ? [
              ...m,
              {
                role: 'system',
                text: answer.answer.trim() || 'No answer was returned — the Researcher model may be offline.',
              },
            ]
          : [...m, { role: 'bot', answer }],
      );
    } catch (err) {
      const msg = errMessage(err);
      toast.showError(`Researcher: ${msg}`);
      setMessages((m) => [...m, { role: 'system', text: `Couldn’t reach the Researcher — ${msg}` }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHead
        title="Researcher"
        subtitle="Grounded, cited chat over news, on-chain, fundamentals and the knowledge base. Ask for a signal-based view and it weighs the evidence both ways — it only refuses what the sources don’t cover."
      />

      <div className="src-strip" style={{ marginBottom: 12 }}>
        <span className="section-title mb-0" style={{ marginRight: 2 }}>
          Stack
        </span>
        <SourceBadge source="openai" />
        <SourceBadge source="pgvector" />
      </div>

      <div className="row gap wrap" style={{ marginBottom: 12 }}>
        <span className="section-title mb-0">Filter</span>
        {UNIVERSE.map((sym) => (
          <button
            key={sym}
            className={`pill ${symbols.includes(sym) ? 'badge accent' : ''}`}
            style={{ cursor: 'pointer' }}
            onClick={() => toggleSymbol(sym)}
            aria-pressed={symbols.includes(sym)}
          >
            {sym}
          </button>
        ))}
      </div>

      <div className="chat-shell">
        <div className="chat-log" ref={logRef}>
          {messages.length === 0 && (
            <div className="chat-empty">
              <p className="chat-empty-lead">
                Ask about coin mechanisms, recent news, on-chain activity or fundamentals — or ask
                for a signal-based view. Every answer is grounded in cited sources.
              </p>
              <div className="sample-qs">
                <span className="sample-qs-label">Try</span>
                {SAMPLE_QUESTIONS.map((q) => (
                  <button
                    key={q}
                    type="button"
                    className="pill sample-q"
                    onClick={() => send(q)}
                    disabled={busy}
                  >
                    {q}
                  </button>
                ))}
              </div>
            </div>
          )}
          {messages.map((m, i) =>
            m.role === 'bot' ? (
              <BotMessage key={i} answer={m.answer} />
            ) : (
              <div key={i} className={`msg ${m.role}`}>
                {m.text}
              </div>
            ),
          )}
          {busy && <div className="msg bot dim">Thinking…</div>}
        </div>

        <div className="chat-input">
          <input
            className="input"
            placeholder="Ask the Researcher…"
            value={input}
            disabled={busy}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()}
          />
          <button className="btn primary" onClick={() => send()} disabled={busy || !input.trim()}>
            Send
          </button>
        </div>
      </div>
    </>
  );
}
