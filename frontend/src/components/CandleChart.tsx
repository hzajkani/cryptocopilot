import { useEffect, useRef, useState } from 'react';
import { dispose, init, registerIndicator, IndicatorSeries, type Chart, type KLineData } from 'klinecharts';
import type { Candle } from '../api/types';

/**
 * KLineCharts candlestick panel with a built-in volume sub-pane and an
 * Ichimoku Kinkō Hyō overlay (PROJECT.md §4). Replaces the earlier
 * TradingView Lightweight Charts panel so every coin gets richer, toggleable
 * technical indicators instead of bare candles.
 */

// --- Ichimoku Kinkō Hyō: registered once for the whole app -----------------

type Ichimoku = {
  tenkan?: number; // Conversion line
  kijun?: number; // Base line
  spanA?: number; // Leading span A  (top/bottom of the cloud)
  spanB?: number; // Leading span B  (top/bottom of the cloud)
  chikou?: number; // Lagging span
};

const ICHIMOKU = 'ICHIMOKU';
const CANDLE_PANE = 'candle_pane';
const VOLUME_PANE = 'volume_pane';

let ichimokuRegistered = false;

function ensureIchimokuRegistered(): void {
  if (ichimokuRegistered) return;
  ichimokuRegistered = true;

  registerIndicator<Ichimoku>({
    name: ICHIMOKU,
    shortName: 'Ichimoku',
    series: IndicatorSeries.Price, // share the candle pane's price scale
    precision: 2,
    // conversion, base, span-B, displacement (the classic 9 / 26 / 52 / 26).
    calcParams: [9, 26, 52, 26],
    figures: [
      { key: 'tenkan', title: 'Tenkan: ', type: 'line', styles: () => ({ color: '#42a5f5' }) },
      { key: 'kijun', title: 'Kijun: ', type: 'line', styles: () => ({ color: '#ef5350' }) },
      { key: 'chikou', title: 'Chikou: ', type: 'line', styles: () => ({ color: '#b39ddb' }) },
      { key: 'spanA', title: 'Span A: ', type: 'line', styles: () => ({ color: '#16c784' }) },
      { key: 'spanB', title: 'Span B: ', type: 'line', styles: () => ({ color: '#ea3943' }) },
    ],
    calc: (dataList, indicator) => {
      const [conv, base, spanBPeriod, disp] = indicator.calcParams as number[];
      const n = dataList.length;

      // Midpoint of the highest high / lowest low over the last `period` bars.
      const midpoint = (period: number, i: number): number | undefined => {
        if (i < period - 1) return undefined;
        let hi = -Infinity;
        let lo = Infinity;
        for (let j = i - period + 1; j <= i; j++) {
          hi = Math.max(hi, dataList[j].high);
          lo = Math.min(lo, dataList[j].low);
        }
        return (hi + lo) / 2;
      };

      const tenkanRaw: Array<number | undefined> = new Array(n);
      const kijunRaw: Array<number | undefined> = new Array(n);
      const spanBRaw: Array<number | undefined> = new Array(n);
      for (let i = 0; i < n; i++) {
        tenkanRaw[i] = midpoint(conv, i);
        kijunRaw[i] = midpoint(base, i);
        spanBRaw[i] = midpoint(spanBPeriod, i);
      }

      const result: Ichimoku[] = new Array(n);
      for (let i = 0; i < n; i++) {
        const row: Ichimoku = { tenkan: tenkanRaw[i], kijun: kijunRaw[i] };

        // Leading spans are plotted `disp` bars forward. KLineCharts has no
        // future bars to draw onto, so the projection is clipped at the last
        // candle — the in-range cloud is correct, the forward tail is omitted.
        const t = tenkanRaw[i - disp];
        const k = kijunRaw[i - disp];
        if (t !== undefined && k !== undefined) row.spanA = (t + k) / 2;
        const b = spanBRaw[i - disp];
        if (b !== undefined) row.spanB = b;

        // Lagging span: the current close plotted `disp` bars back.
        const future = dataList[i + disp];
        if (future !== undefined) row.chikou = future.close;

        result[i] = row;
      }
      return result;
    },
    // Kumo (cloud): fill the band between Span A and Span B. Returning false
    // keeps the five default line figures drawing on top of the fill.
    draw: ({ ctx, indicator, visibleRange, xAxis, yAxis }) => {
      const result = indicator.result;
      const { from, to } = visibleRange;
      ctx.save();
      for (let i = Math.max(from, 1); i < to; i++) {
        const prev = result[i - 1];
        const cur = result[i];
        if (prev?.spanA == null || prev.spanB == null || cur?.spanA == null || cur.spanB == null) {
          continue;
        }
        const x0 = xAxis.convertToPixel(i - 1);
        const x1 = xAxis.convertToPixel(i);
        ctx.beginPath();
        ctx.moveTo(x0, yAxis.convertToPixel(prev.spanA));
        ctx.lineTo(x1, yAxis.convertToPixel(cur.spanA));
        ctx.lineTo(x1, yAxis.convertToPixel(cur.spanB));
        ctx.lineTo(x0, yAxis.convertToPixel(prev.spanB));
        ctx.closePath();
        const bullish = prev.spanA + cur.spanA >= prev.spanB + cur.spanB;
        ctx.fillStyle = bullish ? 'rgba(22,199,132,0.14)' : 'rgba(234,57,67,0.14)';
        ctx.fill();
      }
      ctx.restore();
      return false;
    },
  });
}

// --- data mapping ----------------------------------------------------------

/**
 * Map API candles to KLineCharts rows: drop incomplete candles, convert the
 * ISO timestamp to epoch-ms, then sort ascending and de-duplicate (KLineCharts
 * expects strictly increasing, unique timestamps).
 */
function toKLineData(candles: Candle[]): KLineData[] {
  const seen = new Set<number>();
  return candles
    .filter((c) => c.open != null && c.high != null && c.low != null && c.close != null)
    .map((c) => ({
      timestamp: Date.parse(c.ts),
      open: c.open as number,
      high: c.high as number,
      low: c.low as number,
      close: c.close as number,
      volume: c.volume ?? undefined,
    }))
    .filter((d) => Number.isFinite(d.timestamp))
    .sort((a, b) => a.timestamp - b.timestamp)
    .filter((d) => (seen.has(d.timestamp) ? false : (seen.add(d.timestamp), true)));
}

// --- component -------------------------------------------------------------

export function CandleChart({ candles }: { candles: Candle[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<Chart | null>(null);
  const [showIchimoku, setShowIchimoku] = useState(true);
  const [showVolume, setShowVolume] = useState(true);

  // Init the chart once; data and indicator visibility are handled separately
  // so switching timeframe never tears down and rebuilds the canvas.
  useEffect(() => {
    ensureIchimokuRegistered();
    const el = containerRef.current;
    if (!el) return;

    const chart = init(el, {
      styles: {
        grid: {
          horizontal: { color: '#1b222c' },
          vertical: { color: '#1b222c' },
        },
        candle: {
          bar: {
            upColor: '#16c784',
            downColor: '#ea3943',
            noChangeColor: '#9aa6b2',
            upBorderColor: '#16c784',
            downBorderColor: '#ea3943',
            noChangeBorderColor: '#9aa6b2',
            upWickColor: '#16c784',
            downWickColor: '#ea3943',
            noChangeWickColor: '#9aa6b2',
          },
          priceMark: {
            high: { color: '#9aa6b2' },
            low: { color: '#9aa6b2' },
            last: { text: { color: '#0b0e11' } },
          },
          tooltip: { text: { color: '#9aa6b2' } },
        },
        xAxis: { axisLine: { color: '#232c38' }, tickLine: { color: '#232c38' }, tickText: { color: '#9aa6b2' } },
        yAxis: { axisLine: { color: '#232c38' }, tickLine: { color: '#232c38' }, tickText: { color: '#9aa6b2' } },
        crosshair: {
          horizontal: { text: { color: '#0b0e11' } },
          vertical: { text: { color: '#0b0e11' } },
        },
        indicator: { tooltip: { text: { color: '#9aa6b2' } } },
        separator: { color: '#232c38' },
      },
    });
    if (!chart) return;
    chartRef.current = chart;

    chart.createIndicator(ICHIMOKU, true, { id: CANDLE_PANE });
    chart.createIndicator('VOL', false, { id: VOLUME_PANE, height: 90 });

    return () => {
      dispose(el);
      chartRef.current = null;
    };
  }, []);

  // Feed candles whenever they change (symbol or timeframe switch).
  useEffect(() => {
    chartRef.current?.applyNewData(toKLineData(candles));
  }, [candles]);

  // Toggle indicator visibility without rebuilding the chart.
  useEffect(() => {
    chartRef.current?.overrideIndicator({ name: ICHIMOKU, visible: showIchimoku }, CANDLE_PANE);
  }, [showIchimoku]);

  useEffect(() => {
    chartRef.current?.overrideIndicator({ name: 'VOL', visible: showVolume }, VOLUME_PANE);
  }, [showVolume]);

  return (
    <div className="candle-chart">
      <div className="candle-chart__controls">
        <div className="seg">
          <button className={showIchimoku ? 'active' : ''} onClick={() => setShowIchimoku((v) => !v)}>
            Ichimoku
          </button>
          <button className={showVolume ? 'active' : ''} onClick={() => setShowVolume((v) => !v)}>
            Volume
          </button>
        </div>
      </div>
      <div className="candle-chart__canvas" ref={containerRef} />
    </div>
  );
}
