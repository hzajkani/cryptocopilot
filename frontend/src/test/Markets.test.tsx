import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { MarketsPage } from '../pages/MarketsPage';
import { api } from '../api/client';

// Mock the whole API client; the page should render whatever rows it returns.
vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: { markets: vi.fn() },
}));

const markets = api.markets as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  markets.mockReset();
});

test('Markets renders a row per coin from the mocked client', async () => {
  markets.mockResolvedValue([
    { symbol: 'BTC', price: 70000, change24hPct: 2.5, marketCapUsd: 1.3e12 },
    { symbol: 'ETH', price: 3500, change24hPct: -1.2, marketCapUsd: null },
  ]);

  render(
    <MemoryRouter>
      <MarketsPage />
    </MemoryRouter>,
  );

  expect((await screen.findAllByText('BTC')).length).toBeGreaterThan(0);
  expect(screen.getAllByText('ETH').length).toBeGreaterThan(0);
  // A null market cap must render as a graceful dash, never "null".
  expect(screen.getByText('—')).toBeInTheDocument();
  expect(screen.queryByText(/null/i)).not.toBeInTheDocument();
});
