import { useState } from "react";
import { Draggable } from "@hello-pangea/dnd";
import { Pencil, X } from "lucide-react";

const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];

export default function CardItem({ card, index, onUpdateCard, onDeleteCard }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({
    title: card.title,
    description: card.description || "",
    priority: card.priority || "MEDIUM",
  });

  function startEditing() {
    setDraft({
      title: card.title,
      description: card.description || "",
      priority: card.priority || "MEDIUM",
    });
    setEditing(true);
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (!draft.title.trim()) return;
    onUpdateCard(card, { ...draft, title: draft.title.trim() });
    setEditing(false);
  }

  return (
    // Dragging is disabled while the inline editor is open, otherwise the drag
    // handle swallows clicks meant for the inputs.
    <Draggable draggableId={String(card.id)} index={index} isDragDisabled={editing}>
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          className="card-item"
          data-priority={card.priority}
          style={{
            ...provided.draggableProps.style,
            opacity: snapshot.isDragging ? 0.85 : 1,
          }}
        >
          {editing ? (
            <form className="card-editor" onSubmit={handleSubmit}>
              <input
                autoFocus
                value={draft.title}
                onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                placeholder="Title"
              />
              <textarea
                rows={2}
                value={draft.description}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                placeholder="Description"
              />
              <select
                value={draft.priority}
                onChange={(e) => setDraft({ ...draft, priority: e.target.value })}
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
              <div className="card-editor-actions">
                <button type="submit">Save</button>
                <button type="button" className="link-btn" onClick={() => setEditing(false)}>
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <div className="card-actions">
                <button
                  className="icon-btn"
                  title="Edit card"
                  aria-label={`Edit card "${card.title}"`}
                  onClick={startEditing}
                >
                  <Pencil size={13} />
                </button>
                <button
                  className="icon-btn danger-link"
                  title="Delete card"
                  aria-label={`Delete card "${card.title}"`}
                  onClick={() => onDeleteCard(card)}
                >
                  <X size={14} />
                </button>
              </div>
              <span className="card-priority-badge" data-priority={card.priority}>
                {card.priority?.toLowerCase()}
              </span>
              <div className="card-title">{card.title}</div>
              {card.description && <div className="card-description">{card.description}</div>}
            </>
          )}
        </div>
      )}
    </Draggable>
  );
}
