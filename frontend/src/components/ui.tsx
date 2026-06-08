import { useState, type ReactNode } from 'react';

/**
 * A collapsible panel with a clickable header. `children` is only mounted while
 * open, so a closed panel runs no data fetches inside it (used for the Markets
 * architecture panel, which is closed by default).
 */
export function Collapsible({
  title,
  subtitle,
  defaultOpen = false,
  children,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="panel">
      <div
        className="panel-head"
        role="button"
        tabIndex={0}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setOpen((o) => !o);
          }
        }}
      >
        <div>
          <h3>{title}</h3>
          {subtitle && <div className="dim" style={{ fontSize: 12, marginTop: 2 }}>{subtitle}</div>}
        </div>
        <span className={`chev ${open ? 'open' : ''}`}>▶</span>
      </div>
      {open && <div className="panel-body">{children}</div>}
    </div>
  );
}

export function Card({
  children,
  className = '',
  onClick,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <div className={`card ${onClick ? 'clickable' : ''} ${className}`} onClick={onClick}>
      {children}
    </div>
  );
}

export function PageHead({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="page-head">
      <h1>{title}</h1>
      {subtitle && <p>{subtitle}</p>}
    </div>
  );
}

export function Skeleton({ height = 16, width = '100%' }: { height?: number; width?: number | string }) {
  return <div className="skel" style={{ height, width }} />;
}

/** A grid of placeholder cards while a page loads. */
export function SkeletonCards({ count = 6 }: { count?: number }) {
  return (
    <div className="grid cards">
      {Array.from({ length: count }).map((_, i) => (
        <div className="card" key={i}>
          <Skeleton height={18} width="40%" />
          <div style={{ height: 12 }} />
          <Skeleton height={12} width="80%" />
          <div style={{ height: 8 }} />
          <Skeleton height={12} width="60%" />
        </div>
      ))}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-box error">
      <span className="emoji">⚠️</span>
      <h3>Couldn’t load this</h3>
      <p>{message}</p>
      {onRetry && (
        <button className="btn sm" onClick={onRetry} style={{ marginTop: 12 }}>
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({
  title,
  children,
  emoji = '🗂️',
}: {
  title: string;
  children?: ReactNode;
  emoji?: string;
}) {
  return (
    <div className="state-box">
      <span className="emoji">{emoji}</span>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
    </div>
  );
}

export function Stat({
  label,
  value,
  sub,
  className = '',
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  className?: string;
}) {
  return (
    <div className="stat">
      <div className="label">{label}</div>
      <div className={`value ${className}`}>{value}</div>
      {sub && <div className="sub">{sub}</div>}
    </div>
  );
}

/** A round avatar showing the coin ticker (no external logo dependency). */
export function CoinAvatar({ symbol }: { symbol: string }) {
  return <div className="coin-avatar">{symbol.slice(0, 4)}</div>;
}
