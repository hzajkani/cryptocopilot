package com.cryptocopilot.analyst;

/**
 * The {@code /api/analyst} payload: the fused {@link AnalystOpinion} plus the two fields the brief
 * requires surfaced at the top level — {@code healthSource} (on-chain vs CoinGecko vs unknown; a
 * transparency requirement, never hidden) and the persistent {@code disclaimer}.
 */
public record AnalystResponse(AnalystOpinion opinion, String healthSource, String disclaimer) {
}
