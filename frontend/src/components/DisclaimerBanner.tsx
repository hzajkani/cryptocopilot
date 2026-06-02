import { IconWarn } from './icons';

/**
 * The persistent disclaimer required on EVERY page (PROJECT.md §9). Rendered once
 * in the Layout so it is always visible above the routed content.
 */
export function DisclaimerBanner() {
  return (
    <div className="disclaimer" role="note">
      <IconWarn />
      <span>Decision-support, not financial advice. Paper trading only — no real money, ever.</span>
    </div>
  );
}
