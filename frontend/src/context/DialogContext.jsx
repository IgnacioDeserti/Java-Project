import { createContext, useCallback, useContext, useRef, useState } from "react";
import Modal from "../components/Modal.jsx";

const DialogContext = createContext(null);

/**
 * Promise-based replacements for window.confirm/window.prompt, styled to match the rest
 * of the app instead of the browser's native (and increasingly inconsistent-looking)
 * dialogs. Any component can call useDialog() directly — no prop-drilling needed.
 */
export function DialogProvider({ children }) {
  const [request, setRequest] = useState(null); // { kind, title, message, ...options }
  const resolver = useRef(null);

  const close = useCallback((result) => {
    resolver.current?.(result);
    resolver.current = null;
    setRequest(null);
  }, []);

  const confirm = useCallback(({ title, message, confirmLabel = "Confirm", danger = false }) => {
    return new Promise((resolve) => {
      resolver.current = resolve;
      setRequest({ kind: "confirm", title, message, confirmLabel, danger });
    });
  }, []);

  const prompt = useCallback(({ title, label, initialValue = "", confirmLabel = "Save" }) => {
    return new Promise((resolve) => {
      resolver.current = resolve;
      setRequest({ kind: "prompt", title, label, initialValue, confirmLabel });
    });
  }, []);

  return (
    <DialogContext.Provider value={{ confirm, prompt }}>
      {children}
      {request?.kind === "confirm" && (
        <ConfirmModal
          request={request}
          onCancel={() => close(false)}
          onConfirm={() => close(true)}
        />
      )}
      {request?.kind === "prompt" && (
        <PromptModal
          request={request}
          onCancel={() => close(null)}
          onSubmit={(value) => close(value)}
        />
      )}
    </DialogContext.Provider>
  );
}

export function useDialog() {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error("useDialog must be used within a DialogProvider");
  return ctx;
}

function ConfirmModal({ request, onCancel, onConfirm }) {
  return (
    <Modal onClose={onCancel} labelledBy="confirm-dialog-title">
      <h2 id="confirm-dialog-title" className="dialog-title">
        {request.title}
      </h2>
      <p className="dialog-message">{request.message}</p>
      <div className="dialog-actions">
        <button className="btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button
          className={request.danger ? "btn-danger" : "btn-primary"}
          onClick={onConfirm}
          autoFocus
        >
          {request.confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

function PromptModal({ request, onCancel, onSubmit }) {
  const [value, setValue] = useState(request.initialValue);

  function handleSubmit(e) {
    e.preventDefault();
    if (!value.trim()) return;
    onSubmit(value.trim());
  }

  return (
    <Modal onClose={onCancel} labelledBy="prompt-dialog-title">
      <form onSubmit={handleSubmit}>
        <h2 id="prompt-dialog-title" className="dialog-title">
          {request.title}
        </h2>
        {request.label && (
          <label className="dialog-field-label" htmlFor="prompt-dialog-input">
            {request.label}
          </label>
        )}
        <input
          id="prompt-dialog-input"
          className="dialog-input"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          autoFocus
        />
        <div className="dialog-actions">
          <button type="button" className="btn-secondary" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className="btn-primary">
            {request.confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
