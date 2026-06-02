// Minimal inline stroke icons (no icon dependency). 24x24 viewBox, currentColor.
import type { SVGProps } from 'react';

type P = SVGProps<SVGSVGElement>;

const base = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
};

export const IconMarkets = (p: P) => (
  <svg {...base} {...p}>
    <path d="M3 3v18h18" />
    <path d="M7 14l3-4 3 2 4-6" />
  </svg>
);

export const IconSignals = (p: P) => (
  <svg {...base} {...p}>
    <path d="M4 18V9" />
    <path d="M9 18V5" />
    <path d="M14 18v-7" />
    <path d="M19 18V8" />
  </svg>
);

export const IconAnalyst = (p: P) => (
  <svg {...base} {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);

export const IconChat = (p: P) => (
  <svg {...base} {...p}>
    <path d="M21 15a2 2 0 0 1-2 2H8l-4 4V5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2z" />
  </svg>
);

export const IconTrade = (p: P) => (
  <svg {...base} {...p}>
    <path d="M3 8h14l-3-3" />
    <path d="M21 16H7l3 3" />
  </svg>
);

export const IconPerformance = (p: P) => (
  <svg {...base} {...p}>
    <path d="M3 3v18h18" />
    <path d="M19 9l-5 5-4-4-4 4" />
  </svg>
);

export const IconWarn = (p: P) => (
  <svg {...base} {...p}>
    <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    <path d="M12 9v4" />
    <path d="M12 17h.01" />
  </svg>
);

export const IconBack = (p: P) => (
  <svg {...base} {...p}>
    <path d="M19 12H5" />
    <path d="M12 19l-7-7 7-7" />
  </svg>
);

export const IconExternal = (p: P) => (
  <svg {...base} {...p} width={13} height={13}>
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    <path d="M15 3h6v6" />
    <path d="M10 14 21 3" />
  </svg>
);
