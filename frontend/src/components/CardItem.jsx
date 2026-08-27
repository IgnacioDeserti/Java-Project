import { useState } from "react";
import { Draggable } from "@hello-pangea/dnd";
import { Calendar, Pencil, X } from "lucide-react";
import { dueDateState, formatDueDate } from "../utils/dates.js";
import { LABELS } from "../constants/labels.js";

const PRIORITIES = ["LOW", "MEDIUM", "HIGH"];

function draftFrom(card) {
  return {
    title: card.title,
    description: card.description || "",
    priority: card.priority || "MEDIUM",
    dueDate: card.dueDate || "",
    labels: card.labels || [],
  };
}

export default function CardItem({
  card,
  index,
  dragDisabled = false,
  onUpdateCard,
  onDeleteCard,
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(() => draftFrom(card));

  function startEditing() {
    setDraft(draftFrom(card));
    setEditing(true);
  }

  function toggleLabel(label) {
    setDraft((current) => ({
      ...current,
      labels: current.labels.includes(label)
        ? current.labels.filter((l) => l !== label)
        : [...current.labels, label],
    }));
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (!draft.title.trim()) return;
    onUpdateCard(card, {
      ...draft,
      title: draft.title.trim(),
      // An empty date input means "no due date"; the API expects null, not "".
      dueDate: draft.dueDate || null,
    });
    setEditing(false);
  }

  const labels = card.labels || [];

  return (
    // Dragging is disabled while the inline editor is open (the drag handle would
    // swallow clicks meant for the inputs), and while the board is filtered (see Board.jsx).
    <Draggable draggableId={String(card.id)} index={index} isDragDisabled={editing || dragDisabled}>
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
                aria-label="Card title"
              />
              <textarea
                rows={2}
                value={draft.description}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                placeholder="Description"
                aria-label="Card description"
              />
              <select
                value={draft.priority}
                onChange={(e) => setDraft({ ...draft, priority: e.target.value })}
                aria-label="Priority"
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>

              <label className="card-editor-field">
                <span>Due date</span>
                <input
                  type="date"
                  value={draft.dueDate}
                  onChange={(e) => setDraft({ ...draft, dueDate: e.target.value })}
                />
              </label>

              <div className="card-editor-field">
                <span>Labels</span>
                <div className="label-picker">
                  {LABELS.map((label) => (
                    <button
                      key={label}
                      type="button"
                      className="label-option"
                      data-label={label}
                      aria-pressed={draft.labels.includes(label)}
                      aria-label={`${label.toLowerCase()} label`}
                      onClick={() => toggleLabel(label)}
                    />
                  ))}
                </div>
              </div>

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

              {labels.length > 0 && (
                <div className="card-labels">
                  {labels.map((label) => (
                    <span
                      key={label}
                      className="card-label"
                      data-label={label}
                      title={label.toLowerCase()}
                    />
                  ))}
                </div>
              )}

              <div className="card-title">{card.title}</div>
              {card.description && <div className="card-description">{card.description}</div>}

              <div className="card-meta">
                <span className="card-priority-badge" data-priority={card.priority}>
                  {card.priority?.toLowerCase()}
                </span>
                {card.dueDate && (
                  <span
                    className="card-due"
                    data-state={dueDateState(card.dueDate)}
                    title={`Due ${card.dueDate}`}
                  >
                    <Calendar size={11} />
                    {formatDueDate(card.dueDate)}
                  </span>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </Draggable>
  );
}
