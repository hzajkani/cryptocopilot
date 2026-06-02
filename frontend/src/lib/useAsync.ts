import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api/client';

export type AsyncState<T> = {
  data: T | null;
  loading: boolean;
  error: string | null;
};

/** Turn any thrown value into a human-readable message (ApiError carries one). */
export function errMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return 'Request failed';
}

/**
 * Run an async loader on mount (and whenever `deps` change), tracking
 * loading / data / error. `reload()` re-runs it. Stale results are ignored
 * when deps change or the component unmounts.
 */
export function useAsync<T>(
  fn: () => Promise<T>,
  deps: unknown[],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({
    data: null,
    loading: true,
    error: null,
  });
  const [nonce, setNonce] = useState(0);
  const reload = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: null }));
    fn().then(
      (data) => {
        if (alive) setState({ data, loading: false, error: null });
      },
      (err) => {
        if (alive) setState({ data: null, loading: false, error: errMessage(err) });
      },
    );
    return () => {
      alive = false;
    };
    // fn is intentionally not a dep — callers pass the real deps explicitly.
  }, [...deps, nonce]);

  return { ...state, reload };
}
