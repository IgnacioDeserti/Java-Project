import { useEffect, useMemo, useRef, useState } from "react";
import { DragDropContext, Draggable, Droppable } from "@hello-pangea/dnd";
import { Plus, Search, X } from "lucide-react";
import ColumnItem from "./ColumnItem.jsx";
import { LABELS } from "../constants/labels.js";
import { boards as boardsApi, errorMessage } from "../api/client.js";
import { subscribeToBoard } from "../api/realtime.js";
import { useToast } from "../context/ToastContext.jsx";
import { useDialog } from "../context/DialogContext.jsx";
import { useKeyboardShortcuts } from "../hooks/useKeyboardShortcuts.js";

const NO_PRIORITY_FILTER = "";

export default function Board({ board, onBoardChange, onBoardDeleted }) {
  const [pending, setPending] = useState(false);
  const [search, setSearch] = useState("");
  const [priorityFilter, setPriorityFilter] = useState(NO_PRIORITY_FILTER);
  const [labelFilter, setLabelFilter] = useState([]);
  const searchInputRef = useRef(null);
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
        boardsApi
          .get(board.id)
          .then(onBoardChange)
          .catch(() => {});
      }
    });
    return unsubscribe;
    // Only re-subscribe when switching to a different board — onBoardChange/onBoardDeleted
    // are stable state setters passed down from App, not fresh functions each render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board.id]);

  // --- Filtering ---

  const isFiltering =
    search.trim() !== "" || priorityFilter !== NO_PRIORITY_FILTER || labelFilter.length > 0;

  const visibleColumns = useMemo(() => {
    if (!isFiltering) return board.columns;

    const needle = search.trim().toLowerCase();
    const matches = (card) => {
      if (needle) {
        const haystack = `${card.title} ${card.description || ""}`.toLowerCase();
        if (!haystack.includes(needle)) return false;
      }
      if (priorityFilter && card.priority !== priorityFilter) return false;
      // Any-of, not all-of: picking two colours widens the search rather than narrowing
      // it to cards carrying both, which is how label filters usually behave.
      if (labelFilter.length > 0 && !labelFilter.some((l) => (card.labels || []).includes(l))) {
        return false;
      }
      return true;
    };

    return board.columns.map((column) => ({ ...column, cards: column.cards.filter(matches) }));
  }, [board.columns, isFiltering, search, priorityFilter, labelFilter]);

  const totalCards = board.columns.reduce((sum, c) => sum + c.cards.length, 0);
  const visibleCards = visibleColumns.reduce((sum, c) => sum + c.cards.length, 0);

  function clearFilters() {
    setSearch("");
    setPriorityFilter(NO_PRIORITY_FILTER);
    setLabelFilter([]);
  }

  function toggleLabelFilter(label) {
    setLabelFilter((current) =>
      current.includes(label) ? current.filter((l) => l !== label) : [...current, label]
    );
  }

  useKeyboardShortcuts({
    n: () => handleAddCard(board.columns[0]?.id),
    c: () => handleAddColumn(),
    "/": () => searchInputRef.current?.focus(),
  });

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
    if (source.droppableId === destination.droppableId && source.index === destination.index) {
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
    const title = await prompt({
      title: "Rename column",
      label: "Column title",
      initialValue: currentTitle,
    });
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
    // Guarded because the "n" shortcut aims at the first column, which won't exist on a
    // board whose columns have all been deleted.
    if (!columnId) return;
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
        // These two use ?? deliberately rather than ||: null/"" are meaningful values
        // here (they clear the field), and the API treats an omitted field as cleared.
        dueDate: changes.dueDate ?? card.dueDate ?? null,
        labels: changes.labels ?? card.labels ?? [],
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

      <div className="board-toolbar">
        <div className="search-field">
          <Search size={15} />
          <input
            ref={searchInputRef}
            type="search"
            value={search}
            placeholder="Search cards…  ( / )"
            aria-label="Search cards"
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") {
                setSearch("");
                e.currentTarget.blur();
              }
            }}
          />
        </div>

        <select
          className="filter-select"
          value={priorityFilter}
          aria-label="Filter by priority"
          onChange={(e) => setPriorityFilter(e.target.value)}
        >
          <option value={NO_PRIORITY_FILTER}>All priorities</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>

        <div className="filter-labels" role="group" aria-label="Filter by label">
          {LABELS.map((label) => (
            <button
              key={label}
              type="button"
              className="label-option"
              data-label={label}
              aria-pressed={labelFilter.includes(label)}
              aria-label={`Filter by ${label.toLowerCase()} label`}
              title={label.toLowerCase()}
              onClick={() => toggleLabelFilter(label)}
            />
          ))}
        </div>

        {isFiltering && (
          <span className="filter-summary">
            {visibleCards} of {totalCards} cards
            {/* Reordering is suspended while filtering: a drop position within a filtered
                list doesn't map to an unambiguous position in the real one, so allowing it
                would silently shuffle cards the user can't see. */}
            <span title="Reordering is paused until you clear the filters">· drag paused</span>
            <button className="link-btn" onClick={clearFilters}>
              <X size={12} /> Clear
            </button>
          </span>
        )}
      </div>

      <DragDropContext onDragEnd={handleDragEnd}>
        <Droppable droppableId="board-columns" direction="horizontal" type="COLUMN">
          {(provided) => (
            <div className="columns-row" ref={provided.innerRef} {...provided.droppableProps}>
              {visibleColumns.map((column, index) => (
                <Draggable
                  key={column.id}
                  draggableId={String(column.id)}
                  index={index}
                  isDragDisabled={isFiltering}
                >
                  {(dragProvided) => (
                    <div ref={dragProvided.innerRef} {...dragProvided.draggableProps}>
                      <ColumnItem
                        column={column}
                        dragHandleProps={dragProvided.dragHandleProps}
                        dragDisabled={isFiltering}
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
