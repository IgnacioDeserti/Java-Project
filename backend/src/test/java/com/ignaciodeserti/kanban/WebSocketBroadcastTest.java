package com.ignaciodeserti.kanban;

import com.ignaciodeserti.kanban.dto.AuthDtos.AuthResponse;
import com.ignaciodeserti.kanban.dto.BoardDtos.BoardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real-time board updates end-to-end against an actual embedded server: a
 * genuine STOMP client connects, authenticates with a JWT (the same way the frontend
 * does), subscribes to a board's topic, and a REST mutation on that board must produce
 * a broadcast on that topic — proving StompAuthChannelInterceptor, WebSocketConfig and
 * BoardBroadcaster are wired together correctly, not just individually valid.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketBroadcastTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void creatingACardBroadcastsToSubscribersOfItsBoard() throws Exception {
        AuthResponse session = rest.postForObject(
                "/api/auth/register",
                Map.of("email", "ws-test@example.com", "password", "secret123", "displayName", "WS Test"),
                AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.token());
        BoardResponse board = rest.exchange(
                "/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Realtime board"), headers),
                BoardResponse.class).getBody();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + session.token());

        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        StompSession stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        stompSession.subscribe("/topic/boards/" + board.id(), new StompFrameHandler() {
            @Override
            @SuppressWarnings("rawtypes")
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });

        Thread.sleep(300); // give the subscription time to register server-side

        long columnId = board.columns().get(0).id();
        rest.exchange(
                "/api/boards/" + board.id() + "/columns/" + columnId + "/cards", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "New card"), headers),
                Object.class);

        Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).as("no broadcast received on the board topic").isNotNull();
        assertThat(event.get("type")).isEqualTo("BOARD_UPDATED");

        stompSession.disconnect();
    }

    @Test
    void subscribingToAnotherUsersBoardIsRejected() throws Exception {
        AuthResponse owner = rest.postForObject(
                "/api/auth/register",
                Map.of("email", "ws-owner@example.com", "password", "secret123", "displayName", "Owner"),
                AuthResponse.class);
        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setBearerAuth(owner.token());
        BoardResponse board = rest.exchange(
                "/api/boards", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Owner's board"), ownerHeaders),
                BoardResponse.class).getBody();

        AuthResponse intruder = rest.postForObject(
                "/api/auth/register",
                Map.of("email", "ws-intruder@example.com", "password", "secret123", "displayName", "Intruder"),
                AuthResponse.class);

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + intruder.token());

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        StompSession stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            // A server-sent STOMP ERROR frame (our rejection) is delivered here,
                            // to the session-level handler — handleException is for local/transport
                            // failures instead, so it wouldn't see this.
                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                errors.add(headers.getFirst("message"));
                            }

                            @Override
                            public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
                                errors.add(exception.getMessage());
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        stompSession.subscribe("/topic/boards/" + board.id(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Should never fire — this subscription is expected to be rejected.
            }
        });

        String error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).as("server should have rejected the subscription").isNotNull();

        // The server already closed the connection after sending the rejection — nothing
        // left to disconnect.
        if (stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }
}
