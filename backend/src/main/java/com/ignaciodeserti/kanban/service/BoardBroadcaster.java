package com.ignaciodeserti.kanban.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Tells every client currently subscribed to a board's topic that something changed, so
 * they can refetch it. Deliberately doesn't carry the diff itself — the payload is a
 * cheap ping, and GET /api/boards/{id} stays the single source of truth for board state.
 *
 * Callers run inside @Transactional service methods, so the send is deferred to after
 * the transaction commits — otherwise a client could receive the ping and refetch before
 * the change is actually visible in the database.
 */
@Component
@RequiredArgsConstructor
public class BoardBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyBoardChanged(Long boardId) {
        send(boardId, "BOARD_UPDATED");
    }

    /** Sent instead of BOARD_UPDATED so viewers know to stop refetching a board that's gone. */
    public void notifyBoardDeleted(Long boardId) {
        send(boardId, "BOARD_DELETED");
    }

    private void send(Long boardId, String type) {
        Runnable publish = () -> messagingTemplate.convertAndSend("/topic/boards/" + boardId, Map.of("type", type));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
