import { useEffect, useRef } from "react";

/** True when the user is typing — shortcuts must not hijack keys mid-sentence. */
function isTypingTarget(target) {
  if (!target) return false;
  const tag = target.tagName;
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || target.isContentEditable;
}

/**
 * Binds single-key shortcuts to a map of handlers, e.g. { n: openNewCard, "/": focusSearch }.
 *
 * Deliberately ignores keypresses while a text field is focused, and any press carrying a
 * modifier, so it never shadows the browser's own shortcuts (Ctrl+N, Cmd+F and friends).
 * Handlers are kept in a ref so the listener is attached once rather than re-bound on
 * every render as the callbacks change identity.
 */
export function useKeyboardShortcuts(handlers, { enabled = true } = {}) {
  const handlersRef = useRef(handlers);

  // Updated in an effect rather than during render: a ref write during render is a side
  // effect, and would be discarded if React re-ran the render without committing it.
  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!enabled) return undefined;

    function onKeyDown(event) {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (isTypingTarget(event.target)) return;

      const handler = handlersRef.current[event.key];
      if (handler) {
        event.preventDefault();
        handler(event);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [enabled]);
}
