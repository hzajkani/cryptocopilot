import { NavLink, Outlet } from 'react-router-dom';
import { DisclaimerBanner } from './DisclaimerBanner';
import { LlmToggle } from './LlmToggle';
import {
  IconAnalyst,
  IconChat,
  IconMarkets,
  IconPerformance,
  IconPipeline,
  IconSignals,
  IconTrade,
} from './icons';

const NAV = [
  { to: '/', label: 'Markets', Icon: IconMarkets, end: true },
  { to: '/signals', label: 'Signals', Icon: IconSignals, end: false },
  { to: '/analyst', label: 'Analyst', Icon: IconAnalyst, end: false },
  { to: '/chat', label: 'Researcher', Icon: IconChat, end: false },
  { to: '/trade', label: 'Paper Trades', Icon: IconTrade, end: false },
  { to: '/performance', label: 'Performance', Icon: IconPerformance, end: false },
  { to: '/ml', label: 'ML Pipeline', Icon: IconPipeline, end: false },
];

export function Layout() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">C</div>
          <div>
            <div className="brand-name">CryptoCopilot</div>
            <div className="brand-sub">decision-support</div>
          </div>
        </div>
        <nav className="nav">
          {NAV.map(({ to, label, Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <Icon />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <LlmToggle />
          <div className="sidebar-foot">Paper trading only. No real money, ever.</div>
        </div>
      </aside>

      <main className="main">
        <DisclaimerBanner />
        <div className="content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
