import { X } from "lucide-react";
import Modal from "./Modal.jsx";

const SHORTCUTS = [
  { keys: ["b"], description: "New board" },
  { keys: ["n"], description: "New card in the first column" },
  { keys: ["c"], description: "New column" },
  { keys: ["/"], description: "Focus the search box" },
  { keys: ["Esc"], description: "Close a dialog, or clear the search" },
  { keys: ["?"], description: "Show this help" },
];

export default function ShortcutsHelp({ onClose }) {
  return (
    <Modal onClose={onClose} labelledBy="shortcuts-title" size="sm">
      <div className="modal-header">
        <h2 id="shortcuts-title">Keyboard shortcuts</h2>
        <button className="icon-btn" onClick={onClose} aria-label="Close">
          <X size={18} />
        </button>
      </div>

      <dl className="shortcuts-list">
        {SHORTCUTS.map(({ keys, description }) => (
          <div className="shortcuts-row" key={description}>
            <dt>{description}</dt>
            <dd>
              {keys.map((key) => (
                <kbd key={key}>{key}</kbd>
              ))}
            </dd>
          </div>
        ))}
      </dl>
    </Modal>
  );
}
