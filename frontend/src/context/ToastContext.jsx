import { createContext, useCallback, useContext, useRef, useState } from "react";
import { CheckCircle2, AlertCircle, Info, X } from "lucide-react";

const ToastContext = createContext(null);

const ICONS = { success: CheckCircle2, error: AlertCircle, info: Info };
const AUTO_DISMISS_MS = 5000;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (message, { type = "info" } = {}) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, message, type }]);
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss]
  );

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => {
          const Icon = ICONS[t.type] ?? Info;
          return (
            <div key={t.id} className={`toast toast-${t.type}`}>
              <Icon size={18} className="toast-icon" />
              <span className="toast-message">{t.message}</span>
              <button className="toast-dismiss" onClick={() => dismiss(t.id)} aria-label="Dismiss">
                <X size={14} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within a ToastProvider");
  return ctx;
}
