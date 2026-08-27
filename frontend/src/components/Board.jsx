import { useEffect, useState } from "react";
import { DragDropContext, Draggable, Droppable } from "@hello-pangea/dnd";
import { Plus } from "lucide-react";
import ColumnItem from "./ColumnItem.jsx";
import { boards as boardsApi, errorMessage } from "../api/client.js";
import { subscribeToBoard } from "../api/realtime.js";
import { useToast } from "../context/ToastContext.jsx";
import { useDialog } from "../context/DialogContext.jsx";

export default function Board({ board, onBoardChange, onBoardDeleted }) {
  const [pending, setPending] = useState(false);
  const { showToast } = useToast();
  const { confirm, prompt } = useDialog();

  function fail(err, fallback) {
    showToast(errorMessage(err, fallback), { type: "error" });
  }

  /** Re-fetches the board so the UI matches whatever the backend actually stored. */
  async function refresh() {
    try {
      onBoardChange(await boardsApi.get(board.id));
    } catch (err) {
      fail(err, "Could not refresh the board");
    }
  }

  // Live updates: any change another tab/session makes to this board (or this same tab,
  // harmlessly) triggers a refetch, so the view stays in sync without polling.
  useEffect(() => {
    const unsubscribe = subscribeToBoard(board.id, (event) => {
      if (event.type === "BOARD_DELETED") {
        onBoardDeleted?.();
      } else {
        boardsApi.get(board.id).then(onBoardChange).catch(() => {});
      }
    });
    return unsubscribe;
    // Only re-subscribe when switching to a different board — onBoardChange/onBoardDeleted
    // are stable state setters passed down from App, not fresh functions each render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board.id]);

  // Called by @hello-pangea/dnd whenever a drag ends (successfully or not) — either a
  // card (default type) or a whole column (type: "COLUMN", see the outer Droppable below).
  async function handleDragEnd(result) {
    if (result.type === "COLUMN") {
      await handleColumnDragEnd(result);
    } else {
      await handleCardDragEnd(result);
    }
  }

  async function handleCardDragEnd({ source, destination, draggableId }) {
    // Dropped outside a column, or back where it started: nothing to do.
    if (!destination) return;
    if (
      source.droppableId === destination.droppableId &&
      source.index === destination.index
    ) {
      return;
    }

    // Optimistic local update so the drop feels instant...
    const previous = board;
    const updated = structuredClone(board);
    const sourceColumn = updated.columns.find((c) => String(c.id) === source.droppableId);
    const destColumn = updated.columns.find((c) => String(c.id) === destination.droppableId);
    const [movedCard] = sourceColumn.cards.splice(source.index, 1);
    movedCard.columnId = destColumn.id;
    destColumn.cards.splice(destination.index, 0, movedCard);
    reindex(sourceColumn);
    reindex(destColumn);
    onBoardChange(updated);

    // ...then persist it, rolling back if the backend refuses.
    try {
      setPending(true);
      await boardsApi.moveCard(board.id, Number(draggableId), {
        targetColumnId: Number(destination.droppableId),
        newPosition: destination.index,
      });
    } catch (err) {
      onBoardChange(previous);
      fail(err, "Could not move the card");
    } finally {
      setPending(false);
    }
  }

  async function handleColumnDragEnd({ source, destination, draggableId }) {
    if (!destination || source.index === destination.index) return;

    const previous = board;
    const updated = structuredClone(board);
    const [movedColumn] = updated.columns.splice(source.index, 1);
    updated.columns.splice(destination.index, 0, movedColumn);
    updated.columns.forEach((c, i) => (c.position = i));
    onBoardChange(updated);

    try {
      setPending(true);
      await boardsApi.moveColumn(board.id, Number(draggableId), { newPosition: destination.index });
    } catch (err) {
      onBoardChange(previous);
      fail(err, "Could not move the column");
    } finally {
      setPending(false);
    }
  }

  async function handleAddColumn() {
    const title = await prompt({ title: "New column", label: "Column title", confirmLabel: "Add" });
    if (!title) return;
    try {
      const column = await boardsApi.createColumn(board.id, { title });
      onBoardChange({ ...board, columns: [...board.columns, { ...column, cards: [] }] });
    } catch (err) {
      fail(err, "Could not add the column");
    }
  }

  async function handleRenameColumn(columnId, currentTitle) {
    const title = await prompt({ title: "Rename column", label: "Column title", initialValue: currentTitle });
    if (!title || title === currentTitle) return;
    try {
      await boardsApi.updateColumn(board.id, columnId, { title });
      onBoardChange({
        ...board,
        columns: board.columns.map((c) => (c.id === columnId ? { ...c, title } : c)),
      });
    } catch (err) {
      fail(err, "Could not rename the column");
    }
  }

  async function handleDeleteColumn(columnId, title) {
    const ok = await confirm({
      title: "Delete column?",
      message: `"${title}" and every card in it will be permanently deleted.`,
      confirmLabel: "Delete column",
      danger: true,
    });
    if (!ok) return;
    try {
      await boardsApi.removeColumn(board.id, columnId);
      await refresh(); // positions of the remaining columns shifted server-side
    } catch (err) {
      fail(err, "Could not delete the column");
    }
  }

  async function handleAddCard(columnId) {
    const title = await prompt({ title: "New card", label: "Card title", confirmLabel: "Add" });
    if (!title) return;
    try {
      const newCard = await boardsApi.createCard(board.id, columnId, { title, priority: "MEDIUM" });
      onBoardChange({
        ...board,
        columns: board.columns.map((c) =>
          c.id === columnId ? { ...c, cards: [...c.cards, newCard] } : c
        ),
      });
    } catch (err) {
      fail(err, "Could not add the card");
    }
  }

  async function handleUpdateCard(card, changes) {
    try {
      const saved = await boardsApi.updateCard(board.id, card.id, {
        title: changes.title ?? card.title,
        description: changes.description ?? card.description,
        priority: changes.priority ?? card.priority,
      });
      onBoardChange({
        ...board,
        columns: board.columns.map((c) => ({
          ...c,
          cards: c.cards.map((existing) => (existing.id === card.id ? saved : existing)),
        })),
      });
    } catch (err) {
      fail(err, "Could not save the card");
    }
  }

  async function handleDeleteCard(card) {
    const ok = await confirm({
      title: "Delete card?",
      message: `"${card.title}" will be permanently deleted.`,
      confirmLabel: "Delete card",
      danger: true,
    });
    if (!ok) return;
    try {
      await boardsApi.removeCard(board.id, card.id);
      const updated = structuredClone(board);
      const column = updated.columns.find((c) => c.id === card.columnId);
      column.cards = column.cards.filter((existing) => existing.id !== card.id);
      reindex(column);
      onBoardChange(updated);
    } catch (err) {
      fail(err, "Could not delete the card");
    }
  }

  return (
    <div className="board">
      <div className="board-header">
        <h2>{board.name}</h2>
        {pending && (
          <span className="saving-indicator">
            <span className="spinner" /> Saving…
          </span>
        )}
      </div>

      <DragDropContext onDragEnd={handleDragEnd}>
        <Droppable droppableId="board-columns" direction="horizontal" type="COLUMN">
          {(provided) => (
            <div className="columns-row" ref={provided.innerRef} {...provided.droppableProps}>
              {board.columns.map((column, index) => (
                <Draggable key={column.id} draggableId={String(column.id)} index={index}>
                  {(dragProvided) => (
                    <div ref={dragProvided.innerRef} {...dragProvided.draggableProps}>
                      <ColumnItem
                        column={column}
                        dragHandleProps={dragProvided.dragHandleProps}
                        onAddCard={handleAddCard}
                        onRenameColumn={handleRenameColumn}
                        onDeleteColumn={handleDeleteColumn}
                        onUpdateCard={handleUpdateCard}
                        onDeleteCard={handleDeleteCard}
                      />
                    </div>
                  )}
                </Draggable>
              ))}
              {provided.placeholder}

              <div className="column column-ghost">
                <button className="add-column-btn" onClick={handleAddColumn}>
                  <Plus size={15} />
                  Add column
                </button>
              </div>
            </div>
          )}
        </Droppable>
      </DragDropContext>
    </div>
  );
}

/** Keeps local card positions dense, mirroring what the backend does on its side. */
function reindex(column) {
  column.cards.forEach((card, index) => {
    card.position = index;
  });
}
