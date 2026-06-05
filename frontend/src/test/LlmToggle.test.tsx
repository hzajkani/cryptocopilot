import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { LlmToggle } from '../components/LlmToggle';
import { api } from '../api/client';
import { getLlmProvider, setLlmProvider } from '../lib/llmProvider';

vi.mock('../api/client', () => ({ api: { llmProviders: vi.fn() } }));
const llmProviders = api.llmProviders as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  localStorage.clear();
  setLlmProvider('ollama'); // reset the module-singleton store between tests
});

test('defaults to Ollama and toggles to OpenAI, persisting the choice', async () => {
  llmProviders.mockResolvedValue({ default: 'ollama', ollama: true, openai: true });

  render(<LlmToggle />);
  const sw = screen.getByRole('switch');

  // Default state: Ollama, off.
  expect(sw).toHaveAttribute('aria-checked', 'false');
  // Wait until the providers probe resolves and enables the switch.
  await waitFor(() => expect(sw).toBeEnabled());

  await userEvent.click(sw);

  expect(sw).toHaveAttribute('aria-checked', 'true');
  expect(getLlmProvider()).toBe('openai');
  expect(localStorage.getItem('cc.llmProvider')).toBe('openai');
});

test('disables the switch and stays on Ollama when OpenAI is unavailable', async () => {
  llmProviders.mockResolvedValue({ default: 'ollama', ollama: true, openai: false });

  render(<LlmToggle />);
  const sw = screen.getByRole('switch');

  await waitFor(() => expect(sw).toBeDisabled());
  expect(sw).toHaveAttribute('aria-checked', 'false');
  expect(getLlmProvider()).toBe('ollama');
  expect(screen.getByText(/set openai_api_key to enable/i)).toBeInTheDocument();
});
