import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { ChatPage } from '../pages/ChatPage';
import { ToastProvider } from '../components/Toast';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: { chat: vi.fn() },
}));

const chat = api.chat as unknown as ReturnType<typeof vi.fn>;

test('Chat renders the exact refusal phrase as a calm system message, not an error', async () => {
  const refusal = 'The available sources do not answer this question.';
  chat.mockResolvedValue({
    answer: refusal,
    citations: [],
    retrievedChunks: [],
    latencyMs: 12,
    queryClassification: 'all',
  });

  const user = userEvent.setup();
  render(
    <ToastProvider>
      <ChatPage />
    </ToastProvider>,
  );

  await user.type(screen.getByPlaceholderText(/ask the researcher/i), 'What will BTC be worth in 2030?');
  await user.click(screen.getByRole('button', { name: 'Send' }));

  const message = await screen.findByText(refusal);
  // It must be styled as a system message (.msg.system), not a bot answer bubble.
  expect(message.closest('.msg')).toHaveClass('system');
});
