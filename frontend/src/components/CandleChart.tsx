import { useEffect, useRef } from 'react';
import {
  ColorType,
  CrosshairMode,
  createChart,
  type CandlestickData,
  type UTCTimestamp,
} from 'lightweight-charts';
import type { Candle } from '../api/types';

/** TradingView Lightweight Charts candlestick panel (PROJECT.md §4). */
export function CandleChart({ candles }: { candles: Candle[] }) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const chart = createChart(el, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#9aa6b2',
        fontFamily: '-apple-system, system-ui, sans-serif',
      },
      grid: {
        vertLines: { color: '#1b222c' },
        horzLines: { color: '#1b222c' },
      },
      rightPriceScale: { borderColor: '#232c38' },
      timeScale: { borderColor: '#232c38', timeVisible: true, secondsVisible: false },
      crosshair: { mode: CrosshairMode.Normal },
    });

    const series = chart.addCandlestickSeries({
      upColor: '#16c784',
      downColor: '#ea3943',
      borderVisible: false,
      wickUpColor: '#16c784',
      wickDownColor: '#ea3943',
    });

    // Keep only complete candles, sort ascending, and drop duplicate timestamps
    // (Lightweight Charts requires strictly increasing, unique times).
    const seen = new Set<number>();
    const data: CandlestickData[] = candles
      .filter((c) => c.open != null && c.high != null && c.low != null && c.close != null)
      .map((c) => ({ time: Math.floor(Date.parse(c.ts) / 1000), candle: c }))
      .filter((x) => Number.isFinite(x.time))
      .sort((a, b) => a.time - b.time)
      .filter((x) => (seen.has(x.time) ? false : (seen.add(x.time), true)))
      .map(({ time, candle }) => ({
        time: time as UTCTimestamp,
        open: candle.open as number,
        high: candle.high as number,
        low: candle.low as number,
        close: candle.close as number,
      }));

    series.setData(data);
    chart.timeScale().fitContent();

    return () => chart.remove();
  }, [candles]);

  return <div className="lw-chart" ref={containerRef} />;
}
