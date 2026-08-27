package com.ignaciodeserti.kanban.service;

import com.ignaciodeserti.kanban.config.NotFoundException;
import com.ignaciodeserti.kanban.dto.BoardDtos.*;
import com.ignaciodeserti.kanban.entity.*;
import com.ignaciodeserti.kanban.repository.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    /** Columns every new board starts with, so a fresh board is usable right away. */
    private static final List<String> DEFAULT_COLUMNS = List.of("To Do", "In Progress", "Done");

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final BoardBroadcaster boardBroadcaster;

    // --- Boards ---

    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> listBoards(String userEmail) {
        User owner = getUser(userEmail);
        return boardRepository.findByOwnerOrderByCreatedAtAsc(owner).stream()
                .map(BoardSummaryResponse::from)
                .toList();
    }

    @Transactional
    public BoardResponse createBoard(String userEmail, CreateBoardRequest req) {
        User owner = getUser(userEmail);

        Board board = new Board();
        board.setName(req.name());
        board.setDescription(req.description());
        board.setOwner(owner);
        boardRepository.save(board);

        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            BoardColumn column = new BoardColumn();
            column.setTitle(DEFAULT_COLUMNS.get(i));
            column.setPosition(i);
            column.setBoard(board);
            board.getColumns().add(columnRepository.save(column));
        }

        return BoardResponse.from(board);
    }

    @Transactional(readOnly = true)
    public BoardResponse getBoard(String userEmail, Long boardId) {
        return BoardResponse.from(ownedBoard(userEmail, boardId));
    }

    @Transactional
    public BoardResponse updateBoard(String userEmail, Long boardId, UpdateBoardRequest req) {
        Board board = ownedBoard(userEmail, boardId);
        board.setName(req.name());
        board.setDescription(req.description());
        BoardResponse response = BoardResponse.from(boardRepository.save(board));
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    @Transactional
    public void deleteBoard(String userEmail, Long boardId) {
        boardRepository.delete(ownedBoard(userEmail, boardId));
        boardBroadcaster.notifyBoardDeleted(boardId);
    }

    // --- Columns ---

    @Transactional
    public ColumnResponse createColumn(String userEmail, Long boardId, CreateColumnRequest req) {
        Board board = ownedBoard(userEmail, boardId);

        BoardColumn column = new BoardColumn();
        column.setTitle(req.title());
        column.setPosition(req.position() != null ? req.position() : board.getColumns().size());
        column.setBoard(board);
        columnRepository.save(column);
        board.getColumns().add(column);

        ColumnResponse response = ColumnResponse.from(column);
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    @Transactional
    public ColumnResponse updateColumn(
            String userEmail, Long boardId, Long columnId, UpdateColumnRequest req) {
        BoardColumn column = ownedColumn(userEmail, boardId, columnId);
        column.setTitle(req.title());
        ColumnResponse response = ColumnResponse.from(columnRepository.save(column));
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    /** Moves a column to a new position on the board, re-indexing the others around it. */
    @Transactional
    public ColumnResponse moveColumn(
            String userEmail, Long boardId, Long columnId, MoveColumnRequest req) {
        Board board = ownedBoard(userEmail, boardId);
        BoardColumn column = findColumn(board, columnId);

        List<BoardColumn> columns =
                new ArrayList<>(columnRepository.findByBoardOrderByPositionAsc(board));
        columns.removeIf(c -> c.getId().equals(columnId));
        columns.add(clamp(req.newPosition(), columns.size()), column);

        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).setPosition(i);
        }
        columnRepository.saveAll(columns);

        ColumnResponse response = ColumnResponse.from(column);
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    @Transactional
    public void deleteColumn(String userEmail, Long boardId, Long columnId) {
        Board board = ownedBoard(userEmail, boardId);
        BoardColumn column = findColumn(board, columnId);

        board.getColumns().remove(column);
        columnRepository.delete(column); // cards go with it (cascade + orphanRemoval)

        // Close the gap left in the ordering so positions stay 0..n-1.
        List<BoardColumn> remaining = columnRepository.findByBoardOrderByPositionAsc(board);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        columnRepository.saveAll(remaining);
        boardBroadcaster.notifyBoardChanged(boardId);
    }

    // --- Cards ---

    @Transactional
    public CardResponse createCard(
            String userEmail, Long boardId, Long columnId, CreateCardRequest req) {
        BoardColumn column = ownedColumn(userEmail, boardId, columnId);

        int size = cardRepository.countByColumn(column).intValue();

        Card card = new Card();
        card.setTitle(req.title());
        card.setDescription(req.description());
        card.setPosition(req.position() != null ? clamp(req.position(), size) : size);
        if (req.priority() != null) {
            card.setPriority(req.priority());
        }
        card.setDueDate(req.dueDate());
        replaceLabels(card, req.labels());
        card.setColumn(column);

        CardResponse response = CardResponse.from(cardRepository.save(card));
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    @Transactional
    public CardResponse updateCard(
            String userEmail, Long boardId, Long cardId, UpdateCardRequest req) {
        Card card = ownedCard(userEmail, boardId, cardId);
        card.setTitle(req.title());
        card.setDescription(req.description());
        if (req.priority() != null) {
            card.setPriority(req.priority());
        }
        // A PUT carries the card's whole new state, so an absent due date or label set
        // means "cleared", not "unchanged" — that's what makes removing them possible.
        card.setDueDate(req.dueDate());
        replaceLabels(card, req.labels());
        CardResponse response = CardResponse.from(cardRepository.save(card));
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    @Transactional
    public void deleteCard(String userEmail, Long boardId, Long cardId) {
        Card card = ownedCard(userEmail, boardId, cardId);
        BoardColumn column = card.getColumn();

        column.getCards().remove(card); // orphanRemoval turns this into a DELETE
        cardRepository.flush(); // delete before we renumber, so positions don't collide

        reindex(cardsOf(column));
        boardBroadcaster.notifyBoardChanged(boardId);
    }

    /**
     * Moves a card to a (possibly different) column and re-indexes both columns so positions stay a
     * dense 0..n-1 sequence. This backs the drag-and-drop interaction.
     */
    @Transactional
    public CardResponse moveCard(String userEmail, Long boardId, Long cardId, MoveCardRequest req) {
        Board board = ownedBoard(userEmail, boardId);
        Card card = findOwnedCard(board, cardId);

        BoardColumn source = card.getColumn();
        BoardColumn target =
                req.targetColumnId() == null ? source : findColumn(board, req.targetColumnId());

        // Work on detached copies of the orderings: the entity collections are the
        // inverse side, and mutating them would trip orphanRemoval on a move.
        List<Card> sourceCards = cardsOf(source);
        sourceCards.removeIf(c -> c.getId().equals(cardId));

        if (source.getId().equals(target.getId())) {
            sourceCards.add(clamp(req.newPosition(), sourceCards.size()), card);
            reindex(sourceCards);
        } else {
            reindex(sourceCards);

            List<Card> targetCards = cardsOf(target);
            card.setColumn(target);
            targetCards.add(clamp(req.newPosition(), targetCards.size()), card);
            reindex(targetCards);
        }

        CardResponse response = CardResponse.from(card);
        boardBroadcaster.notifyBoardChanged(boardId);
        return response;
    }

    // --- Helpers ---

    private List<Card> cardsOf(BoardColumn column) {
        return new ArrayList<>(cardRepository.findByColumnOrderByPositionAsc(column));
    }

    /**
     * Mutates the card's existing label collection in place rather than swapping in a new one:
     * Hibernate tracks the managed collection instance, so replacing the reference makes it drop
     * and re-insert every row instead of diffing them.
     */
    private void replaceLabels(Card card, Set<Card.Label> labels) {
        card.getLabels().clear();
        if (labels != null) {
            card.getLabels().addAll(labels);
        }
    }

    private void reindex(List<Card> cards) {
        for (int i = 0; i < cards.size(); i++) {
            cards.get(i).setPosition(i);
        }
        cardRepository.saveAll(cards);
    }

    /** Keeps a requested index inside [0, size]; a null index means "append". */
    private int clamp(Integer requested, int size) {
        if (requested == null) return size;
        return Math.max(0, Math.min(requested, size));
    }

    private Board ownedBoard(String userEmail, Long boardId) {
        User owner = getUser(userEmail);
        return boardRepository
                .findByIdAndOwner(boardId, owner)
                .orElseThrow(() -> new NotFoundException("Board not found"));
    }

    private BoardColumn ownedColumn(String userEmail, Long boardId, Long columnId) {
        return findColumn(ownedBoard(userEmail, boardId), columnId);
    }

    private BoardColumn findColumn(Board board, Long columnId) {
        return board.getColumns().stream()
                .filter(c -> c.getId().equals(columnId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Column not found on this board"));
    }

    private Card ownedCard(String userEmail, Long boardId, Long cardId) {
        return findOwnedCard(ownedBoard(userEmail, boardId), cardId);
    }

    /** Looks the card up through the board, so one user can never touch another's card by id. */
    private Card findOwnedCard(Board board, Long cardId) {
        Card card =
                cardRepository
                        .findById(cardId)
                        .orElseThrow(() -> new NotFoundException("Card not found"));
        if (!card.getColumn().getBoard().getId().equals(board.getId())) {
            throw new NotFoundException("Card not found on this board");
        }
        return card;
    }

    private User getUser(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
