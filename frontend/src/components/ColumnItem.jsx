import { Droppable } from "@hello-pangea/dnd";
import { Plus, X } from "lucide-react";
import CardItem from "./CardItem.jsx";

export default function ColumnItem({
  column,
  dragHandleProps,
  dragDisabled = false,
  onAddCard,
  onRenameColumn,
  onDeleteColumn,
  onUpdateCard,
  onDeleteCard,
}) {
  const cards = column.cards || [];

  return (
    <div className="column">
      {/* The header is the column's drag handle — consistent with cards, where the
          whole card is draggable but its buttons still work via dnd's click/drag
          disambiguation. */}
      <div className="column-header" {...dragHandleProps}>
        <button
          type="button"
          className="column-title"
          title="Click to rename"
          aria-label={`Rename column "${column.title}"`}
          onClick={() => onRenameColumn(column.id, column.title)}
        >
          {column.title}
        </button>
        <div className="column-header-right">
          <span className="card-count">{cards.length}</span>
          <button
            className="danger-link"
            title="Delete column"
            aria-label={`Delete column "${column.title}"`}
            onClick={() => onDeleteColumn(column.id, column.title)}
          >
            <X size={15} />
          </button>
        </div>
      </div>

      <Droppable droppableId={String(column.id)}>
        {(provided, snapshot) => (
          <div
            ref={provided.innerRef}
            {...provided.droppableProps}
            className={`column-body ${snapshot.isDraggingOver ? "dragging-over" : ""}`}
          >
            {cards.map((card, index) => (
              <CardItem
                key={card.id}
                card={card}
                index={index}
                dragDisabled={dragDisabled}
                onUpdateCard={onUpdateCard}
                onDeleteCard={onDeleteCard}
              />
            ))}
            {provided.placeholder}
          </div>
        )}
      </Droppable>

      <button className="add-card-btn" onClick={() => onAddCard(column.id)}>
        <Plus size={14} />
        Add card
      </button>
    </div>
  );
}
