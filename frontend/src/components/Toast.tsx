import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

type ToastKind = 'error' | 'info' | 'success';
type Toast = { id: number; kind: ToastKind; message: string };

type ToastApi = {
  showError: (message: string) => void;
  showInfo: (message: string) => void;
  showSuccess: (message: string) => void;
};

const ToastContext = createContext<ToastApi | null>(null);

/** The single global surface for transient messages — chiefly non-2xx errors. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);

  const remove = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string) => {
      const id = ++seq.current;
      setToasts((t) => [...t, { id, kind, message }]);
      window.setTimeout(() => remove(id), 6000);
    },
    [remove],
  );

  const apiValue = useMemo<ToastApi>(
    () => ({
      showError: (m) => push('error', m),
      showInfo: (m) => push('info', m),
      showSuccess: (m) => push('success', m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={apiValue}>
      {children}
      <div className="toast-wrap" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.kind}`}>
            <span style={{ flex: 1 }}>{t.message}</span>
            <button className="close" aria-label="Dismiss" onClick={() => remove(t.id)}>
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within a ToastProvider');
  return ctx;
}
