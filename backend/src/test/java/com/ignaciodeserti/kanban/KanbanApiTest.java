package com.ignaciodeserti.kanban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KanbanApiTest {

    @Autowired MockMvc mvc;

    @Autowired ObjectMapper json;

    // --- Auth ---

    @Test
    void registersLogsInAndReadsCurrentUser() throws Exception {
        String token = register("alice@example.com", "Alice");

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"alice@example.com","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.displayName").value("Alice"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void rejectsWrongPasswordWith401() throws Exception {
        register("bob@example.com", "Bob");

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"bob@example.com","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnonymousAndGarbageTokensWith401() throws Exception {
        mvc.perform(get("/api/boards")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/boards").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsShortPasswordWith400() throws Exception {
        mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"tiny@example.com","password":"123","displayName":"Tiny"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // --- Boards & columns ---

    @Test
    void newBoardStartsWithTheThreeDefaultColumns() throws Exception {
        String token = register("carol@example.com", "Carol");
        JsonNode board = createBoard(token, "Sprint 1");

        assertThat(board.get("columns")).hasSize(3);
        assertThat(board.get("columns").get(0).get("title").asText()).isEqualTo("To Do");
        assertThat(board.get("columns").get(2).get("title").asText()).isEqualTo("Done");
    }

    @Test
    void addsAndDeletesColumnsKeepingPositionsDense() throws Exception {
        String token = register("dave@example.com", "Dave");
        JsonNode board = createBoard(token, "Roadmap");
        long boardId = board.get("id").asLong();
        long firstColumnId = board.get("columns").get(0).get("id").asLong();

        mvc.perform(
                        post("/api/boards/" + boardId + "/columns")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"title":"Backlog"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(3));

        mvc.perform(
                        delete("/api/boards/" + boardId + "/columns/" + firstColumnId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode reloaded = getBoard(token, boardId);
        assertThat(reloaded.get("columns")).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(reloaded.get("columns").get(i).get("position").asInt()).isEqualTo(i);
        }
    }

    @Test
    void movingAColumnReindexesTheOthersAroundIt() throws Exception {
        String token = register("henry@example.com", "Henry");
        JsonNode board = createBoard(token, "Pipeline"); // To Do(0), In Progress(1), Done(2)
        long boardId = board.get("id").asLong();
        long todoId = board.get("columns").get(0).get("id").asLong();
        long inProgressId = board.get("columns").get(1).get("id").asLong();

        // Drag "To Do" to the end: [In Progress, Done, To Do].
        mvc.perform(
                        patch("/api/boards/" + boardId + "/columns/" + todoId + "/move")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"newPosition\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));

        JsonNode columns = getBoard(token, boardId).get("columns");
        assertThat(columns.get(0).get("id").asLong()).isEqualTo(inProgressId);
        assertThat(columns.get(0).get("position").asInt()).isZero();
        assertThat(columns.get(2).get("id").asLong()).isEqualTo(todoId);
        assertThat(columns.get(2).get("position").asInt()).isEqualTo(2);
    }

    // --- Cards & drag-and-drop ---

    @Test
    void movingACardAcrossColumnsReindexesBothSides() throws Exception {
        String token = register("erin@example.com", "Erin");
        JsonNode board = createBoard(token, "Work");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long doing = board.get("columns").get(1).get("id").asLong();

        long a = createCard(token, boardId, todo, "A").get("id").asLong();
        long b = createCard(token, boardId, todo, "B").get("id").asLong();
        long c = createCard(token, boardId, todo, "C").get("id").asLong();
        createCard(token, boardId, doing, "X");

        // Drag "B" (index 1 of To Do) to the top of In Progress.
        mvc.perform(
                        patch("/api/boards/" + boardId + "/cards/" + b + "/move")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetColumnId\":" + doing + ",\"newPosition\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnId").value(doing))
                .andExpect(jsonPath("$.position").value(0));

        JsonNode reloaded = getBoard(token, boardId);
        JsonNode todoCards = reloaded.get("columns").get(0).get("cards");
        JsonNode doingCards = reloaded.get("columns").get(1).get("cards");

        // Source closed its gap: A=0, C=1 (no hole where B used to be).
        assertThat(todoCards).hasSize(2);
        assertThat(todoCards.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(todoCards.get(0).get("position").asInt()).isZero();
        assertThat(todoCards.get(1).get("id").asLong()).isEqualTo(c);
        assertThat(todoCards.get(1).get("position").asInt()).isEqualTo(1);

        // Target made room: B=0, X pushed to 1.
        assertThat(doingCards).hasSize(2);
        assertThat(doingCards.get(0).get("id").asLong()).isEqualTo(b);
        assertThat(doingCards.get(1).get("title").asText()).isEqualTo("X");
        assertThat(doingCards.get(1).get("position").asInt()).isEqualTo(1);
    }

    @Test
    void reordersCardsWithinTheSameColumn() throws Exception {
        String token = register("frank@example.com", "Frank");
        JsonNode board = createBoard(token, "Personal");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();

        long a = createCard(token, boardId, todo, "A").get("id").asLong();
        long bId = createCard(token, boardId, todo, "B").get("id").asLong();
        long cId = createCard(token, boardId, todo, "C").get("id").asLong();

        // Drag "C" to the top of its own column.
        mvc.perform(
                        patch("/api/boards/" + boardId + "/cards/" + cId + "/move")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetColumnId\":" + todo + ",\"newPosition\":0}"))
                .andExpect(status().isOk());

        JsonNode cards = getBoard(token, boardId).get("columns").get(0).get("cards");
        assertThat(cards.get(0).get("id").asLong()).isEqualTo(cId);
        assertThat(cards.get(1).get("id").asLong()).isEqualTo(a);
        assertThat(cards.get(2).get("id").asLong()).isEqualTo(bId);
    }

    @Test
    void updatesAndDeletesACardClosingTheGap() throws Exception {
        String token = register("gina@example.com", "Gina");
        JsonNode board = createBoard(token, "Chores");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();

        createCard(token, boardId, todo, "A");
        long b = createCard(token, boardId, todo, "B").get("id").asLong();
        createCard(token, boardId, todo, "C");

        mvc.perform(
                        put("/api/boards/" + boardId + "/cards/" + b)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"title":"B renamed","description":"with notes","priority":"HIGH"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("B renamed"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        mvc.perform(
                        delete("/api/boards/" + boardId + "/cards/" + b)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode cards = getBoard(token, boardId).get("columns").get(0).get("cards");
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).get("position").asInt()).isZero();
        assertThat(cards.get(1).get("position").asInt()).isEqualTo(1);
    }

    // --- Ownership ---

    @Test
    void oneUserCannotReachAnotherUsersBoardOrCards() throws Exception {
        String ownerToken = register("owner@example.com", "Owner");
        JsonNode board = createBoard(ownerToken, "Private");
        long boardId = board.get("id").asLong();
        long todo = board.get("columns").get(0).get("id").asLong();
        long cardId = createCard(ownerToken, boardId, todo, "Secret").get("id").asLong();

        String intruderToken = register("intruder@example.com", "Intruder");

        mvc.perform(get("/api/boards").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(
                        get("/api/boards/" + boardId)
                                .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // Even with their own board, the intruder cannot pull someone else's card into it.
        long intruderBoardId = createBoard(intruderToken, "Mine").get("id").asLong();
        long intruderColumnId =
                getBoard(intruderToken, intruderBoardId).get("columns").get(0).get("id").asLong();

        mvc.perform(
                        patch("/api/boards/" + intruderBoardId + "/cards/" + cardId + "/move")
                                .header("Authorization", "Bearer " + intruderToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"targetColumnId\":"
                                                + intruderColumnId
                                                + ",\"newPosition\":0}"))
                .andExpect(status().isNotFound());
    }

    // --- Helpers ---

    private String register(String email, String displayName) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"email":"%s","password":"secret123","displayName":"%s"}"""
                                                        .formatted(email, displayName)))
                        .andExpect(status().isOk())
                        .andReturn();
        return body(result).get("token").asText();
    }

    private JsonNode createBoard(String token, String name) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/boards")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"name":"%s"}"""
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return body(result);
    }

    private JsonNode getBoard(String token, long boardId) throws Exception {
        MvcResult result =
                mvc.perform(
                                get("/api/boards/" + boardId)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
        return body(result);
    }

    private JsonNode createCard(String token, long boardId, long columnId, String title)
            throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/boards/" + boardId + "/columns/" + columnId + "/cards")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"title":"%s"}"""
                                                        .formatted(title)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return body(result);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
