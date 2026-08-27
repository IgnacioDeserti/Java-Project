package com.ignaciodeserti.kanban.controller;

import com.ignaciodeserti.kanban.dto.BoardDtos.*;
import com.ignaciodeserti.kanban.service.BoardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // --- Boards ---

    @GetMapping
    public List<BoardSummaryResponse> listBoards(@AuthenticationPrincipal UserDetails user) {
        return boardService.listBoards(user.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse createBoard(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody CreateBoardRequest req) {
        return boardService.createBoard(user.getUsername(), req);
    }

    @GetMapping("/{boardId}")
    public BoardResponse getBoard(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long boardId) {
        return boardService.getBoard(user.getUsername(), boardId);
    }

    @PutMapping("/{boardId}")
    public BoardResponse updateBoard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @Valid @RequestBody UpdateBoardRequest req) {
        return boardService.updateBoard(user.getUsername(), boardId, req);
    }

    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> deleteBoard(
            @AuthenticationPrincipal UserDetails user, @PathVariable Long boardId) {
        boardService.deleteBoard(user.getUsername(), boardId);
        return ResponseEntity.noContent().build();
    }

    // --- Columns ---

    @PostMapping("/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse createColumn(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @Valid @RequestBody CreateColumnRequest req) {
        return boardService.createColumn(user.getUsername(), boardId, req);
    }

    @PutMapping("/{boardId}/columns/{columnId}")
    public ColumnResponse updateColumn(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long columnId,
            @Valid @RequestBody UpdateColumnRequest req) {
        return boardService.updateColumn(user.getUsername(), boardId, columnId, req);
    }

    // Drag-and-drop for columns lands here.
    @PatchMapping("/{boardId}/columns/{columnId}/move")
    public ColumnResponse moveColumn(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long columnId,
            @RequestBody MoveColumnRequest req) {
        return boardService.moveColumn(user.getUsername(), boardId, columnId, req);
    }

    @DeleteMapping("/{boardId}/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long columnId) {
        boardService.deleteColumn(user.getUsername(), boardId, columnId);
        return ResponseEntity.noContent().build();
    }

    // --- Cards ---

    @PostMapping("/{boardId}/columns/{columnId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse createCard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long columnId,
            @Valid @RequestBody CreateCardRequest req) {
        return boardService.createCard(user.getUsername(), boardId, columnId, req);
    }

    @PutMapping("/{boardId}/cards/{cardId}")
    public CardResponse updateCard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long cardId,
            @Valid @RequestBody UpdateCardRequest req) {
        return boardService.updateCard(user.getUsername(), boardId, cardId, req);
    }

    @DeleteMapping("/{boardId}/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long cardId) {
        boardService.deleteCard(user.getUsername(), boardId, cardId);
        return ResponseEntity.noContent().build();
    }

    // Drag-and-drop lands here: the frontend calls this when a card is dropped
    // on a new column/position.
    @PatchMapping("/{boardId}/cards/{cardId}/move")
    public CardResponse moveCard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long cardId,
            @RequestBody MoveCardRequest req) {
        return boardService.moveCard(user.getUsername(), boardId, cardId, req);
    }
}
