package com.ignaciodeserti.kanban.dto;

import com.ignaciodeserti.kanban.entity.Board;
import com.ignaciodeserti.kanban.entity.BoardColumn;
import com.ignaciodeserti.kanban.entity.Card;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class BoardDtos {

    // --- Requests ---
    // Size limits mirror the DB columns (see Board/BoardColumn/Card entities) so a
    // too-long input comes back as a clean 400 instead of a DataIntegrityViolation 500.

    public record CreateBoardRequest(
            @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {}

    public record UpdateBoardRequest(
            @NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {}

    public record CreateColumnRequest(@NotBlank @Size(max = 100) String title, Integer position) {}

    public record UpdateColumnRequest(@NotBlank @Size(max = 100) String title) {}

    /** Used when dragging a column to a new position on the board. */
    public record MoveColumnRequest(Integer newPosition) {}

    public record CreateCardRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Integer position,
            Card.Priority priority) {}

    public record UpdateCardRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Card.Priority priority) {}

    /** Used when dragging a card to a new column/position. */
    public record MoveCardRequest(Long targetColumnId, Integer newPosition) {}

    // --- Responses ---
    // Entities are never serialized directly: they carry lazy associations and the
    // owner's data, and Jackson would either blow up or leak fields we don't want.

    public record CardResponse(
            Long id,
            String title,
            String description,
            Integer position,
            Card.Priority priority,
            Long columnId) {
        public static CardResponse from(Card card) {
            return new CardResponse(
                    card.getId(),
                    card.getTitle(),
                    card.getDescription(),
                    card.getPosition(),
                    card.getPriority(),
                    card.getColumn().getId());
        }
    }

    public record ColumnResponse(
            Long id, String title, Integer position, List<CardResponse> cards) {
        public static ColumnResponse from(BoardColumn column) {
            List<CardResponse> cards =
                    column.getCards().stream()
                            .sorted(Comparator.comparing(Card::getPosition))
                            .map(CardResponse::from)
                            .toList();
            return new ColumnResponse(
                    column.getId(), column.getTitle(), column.getPosition(), cards);
        }
    }

    /**
     * Board without its columns — used for the board list, so we don't drag the whole tree along.
     */
    public record BoardSummaryResponse(
            Long id, String name, String description, Instant createdAt) {
        public static BoardSummaryResponse from(Board board) {
            return new BoardSummaryResponse(
                    board.getId(), board.getName(), board.getDescription(), board.getCreatedAt());
        }
    }

    /** Full board: columns and their cards, ordered. This is what the frontend renders. */
    public record BoardResponse(
            Long id,
            String name,
            String description,
            Instant createdAt,
            List<ColumnResponse> columns) {
        public static BoardResponse from(Board board) {
            List<ColumnResponse> columns =
                    board.getColumns().stream()
                            .sorted(Comparator.comparing(BoardColumn::getPosition))
                            .map(ColumnResponse::from)
                            .toList();
            return new BoardResponse(
                    board.getId(),
                    board.getName(),
                    board.getDescription(),
                    board.getCreatedAt(),
                    columns);
        }
    }
}
