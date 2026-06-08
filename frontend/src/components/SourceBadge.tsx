// Data-source badges (demo). Every datum on screen can show which public source
// it came from (PROJECT.md §6). The pill colour is the source API's brand colour;
// unknown keys fall back to a neutral grey so nothing ever renders raw.
import type { Citation } from '../api/types';

export type SourceKey =
  | 'binance'
  | 'coingecko'
  | 'blockchain'
  | 'etherscan'
  | 'news'
  | 'openai'
  | 'ta4j'
  | 'pgvector'
  | 'kb';

const LABELS: Record<SourceKey, string> = {
  binance: 'BINANCE',
  coingecko: 'COINGECKO',
  blockchain: 'BLOCKCHAIN',
  etherscan: 'ETHERSCAN',
  news: 'NEWS RSS',
  openai: 'GPT-4o-mini',
  ta4j: 'ta4j',
  pgvector: 'pgvector RAG',
  kb: 'KNOWLEDGE BASE',
};

const TITLES: Record<SourceKey, string> = {
  binance: 'Binance public API — OHLCV candles',
  coingecko: 'CoinGecko Demo — market cap, supply, fundamentals',
  blockchain: 'Blockchain.com Charts — BTC on-chain metrics',
  etherscan: 'Etherscan — ETH on-chain metrics',
  news: 'RSS feeds — CoinDesk, Cointelegraph, Decrypt, The Block, Bitcoin Magazine',
  openai: 'LLM that phrases the answer/summary (OpenAI gpt-4o-mini or local Ollama)',
  ta4j: 'ta4j (Java) — Ichimoku / RSI / MACD / Bollinger recomputed independently',
  pgvector: 'Spring AI + pgvector — embedded retrieval store',
  kb: 'Curated knowledge base — coin mechanism / tokenomics',
};

/** A single coloured source pill. `label` overrides the default text (e.g. a news outlet name). */
export function SourceBadge({
  source,
  label,
  title,
}: {
  source: SourceKey;
  label?: string;
  title?: string;
}) {
  return (
    <span className={`src-badge ${source}`} title={title ?? TITLES[source]}>
      {label ?? LABELS[source]}
    </span>
  );
}

/** Pick the data-source for a RAG citation from its sourceType (+ symbol for on-chain). */
export function citationSourceKey(c: Citation): SourceKey {
  const t = (c.sourceType || '').toLowerCase();
  if (t === 'news') return 'news';
  if (t === 'fundamental') return 'coingecko';
  if (t === 'onchain') return (c.symbol ?? '').toUpperCase() === 'ETH' ? 'etherscan' : 'blockchain';
  return 'kb'; // 'kb' and anything unknown
}

/** A citation's source rendered as a badge — its real `source` name for news, else the type label. */
export function CitationSourceBadge({ citation }: { citation: Citation }) {
  const key = citationSourceKey(citation);
  const label = key === 'news' && citation.source ? citation.source.toUpperCase() : undefined;
  return <SourceBadge source={key} label={label} title={`Source type: ${citation.sourceType}`} />;
}
