import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, test, vi } from 'vitest';
import { TradePage } from '../pages/TradePage';
import { ToastProvider } from '../components/Toast';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: {
    account: vi.fn(),
    positions: vi.fn(),
    trades: vi.fn(),
    orders: vi.fn(),
    submitOrder: vi.fn(),
    resetAccount: vi.fn(),
  },
}));

const mocked = api as unknown as Record<string, ReturnType<typeof vi.fn>>;

beforeEach(() => {
  mocked.account.mockResolvedValue({ tsUtc: '2026-06-01T00:00:00Z', cashUsd: 10000, totalEquityUsd: 10000 });
  mocked.positions.mockResolvedValue([]);
  mocked.trades.mockResolvedValue([]);
  mocked.orders.mockResolvedValue([]);
});

test('the order ticket disables the limit price for MARKET and enables it for LIMIT', async () => {
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <ToastProvider>
        <TradePage />
      </ToastProvider>
    </MemoryRouter>,
  );

  // MARKET is the default -> limit price is disabled.
  const limit = screen.getByLabelText(/limit price/i);
  expect(limit).toBeDisabled();

  // Switching to LIMIT enables it.
  await user.click(screen.getByRole('button', { name: 'LIMIT' }));
  expect(screen.getByLabelText(/limit price/i)).toBeEnabled();

  // Flush the on-mount data loads so no state update happens after the test.
  await screen.findByText('No open positions.');
});
