// Human-readable labels for the model's raw feature keys (the `featureName` on
// each SHAP driver). The raw keys are the model's machine identity — they're the
// columns the XGBoost bundle was trained on, the `prediction_drivers.feature_name`
// key in Postgres, and the grouping key for the SHAP chart — so they MUST stay
// as-is end-to-end. This map is display-only: it turns `sma90_ratio` into
// "Price / SMA-90" wherever a driver is shown to a person, and nowhere else.
//
// Keys mirror ml/ml/features/{indicators,ichimoku,calendar}.py. Coin one-hots are
// excluded upstream, so only these market-state features ever reach the UI.

const FEATURE_LABELS: Record<string, string> = {
  // --- multi-horizon returns ---
  ret_1h: '1h return',
  ret_4h: '4h return',
  ret_24h: '24h return',
  ret_7d: '7d return',

  // --- RSI (momentum) at three scales ---
  rsi_7: 'RSI (7)',
  rsi_14: 'RSI (14)',
  rsi_21: 'RSI (21)',

  // --- MACD(12,26,9), price-normalized ---
  macd: 'MACD line',
  macd_signal: 'MACD signal',
  macd_hist: 'MACD histogram',
  macd_bull: 'MACD bull cross',

  // --- Stochastic oscillator ---
  stoch_k: 'Stochastic %K',
  stoch_d: 'Stochastic %D',

  // --- trend / volatility ---
  adx: 'ADX trend strength',
  bb_pct: 'Bollinger %B',
  bb_bandwidth: 'Bollinger width',
  atr_pct: 'ATR (% price)',
  ret_vol_24h: 'Realized vol (24h)',
  ret_vol_7d: 'Realized vol (7d)',
  vol_zscore: 'Volume z-score',

  // --- price vs moving averages ---
  sma7_ratio: 'Price / SMA-7',
  sma30_ratio: 'Price / SMA-30',
  sma90_ratio: 'Price / SMA-90',

  // --- Ichimoku cloud ---
  ichimoku_above_cloud: 'Above cloud',
  ichimoku_below_cloud: 'Below cloud',
  ichimoku_in_cloud: 'Inside cloud',
  ichimoku_tk_cross_bull: 'TK bull cross',
  ichimoku_tk_cross_bear: 'TK bear cross',
  ichimoku_cloud_thickness: 'Cloud thickness',
  ichimoku_chikou_clear: 'Chikou clear',
  ichimoku_dist_tenkan: 'Dist. to Tenkan',
  ichimoku_dist_kijun: 'Dist. to Kijun',
  ichimoku_tk_diff: 'Tenkan−Kijun gap',

  // --- calendar / seasonality ---
  hour_of_day: 'Hour of day',
  day_of_week: 'Day of week',
  is_weekend: 'Weekend',
};

/**
 * Friendly label for a raw feature key. Unknown keys (e.g. a feature added later
 * without a map entry) degrade gracefully to a humanized form rather than showing
 * raw snake_case.
 */
export function featureLabel(name: string): string {
  if (!name) return name;
  const known = FEATURE_LABELS[name];
  if (known) return known;
  return name.replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase());
}
