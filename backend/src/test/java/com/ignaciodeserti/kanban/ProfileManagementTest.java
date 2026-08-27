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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileManagementTest {

    @Autowired MockMvc mvc;

    @Autowired ObjectMapper json;

    @Test
    void renamesTheAccount() throws Exception {
        String token = register("rename-me@example.com", "Old Name");

        mvc.perform(
                        put("/api/auth/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.displayName").value("New Name"));
    }

    @Test
    void changesPasswordAndRevokesOtherSessions() throws Exception {
        JsonNode session = registerFullSession("change-pw@example.com", "Changer");
        String token = session.get("token").asText();
        String refreshToken = session.get("refreshToken").asText();

        mvc.perform(
                        post("/api/auth/change-password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"secret123\",\"newPassword\":\"new-secret-456\"}"))
                .andExpect(status().isOk());

        // The refresh token issued before the change is now dead.
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"change-pw@example.com","password":"new-secret-456"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() throws Exception {
        String token = register("wrong-current@example.com", "Wrong");

        mvc.perform(
                        post("/api/auth/change-password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"not-the-real-password\",\"newPassword\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletesTheAccountAndEverythingWithIt() throws Exception {
        String token = register("delete-me@example.com", "Deleter");

        JsonNode board = createBoard(token, "Will be gone");
        assertThat(board.get("id")).isNotNull();

        // Wrong password: refused, account untouched.
        mvc.perform(
                        delete("/api/auth/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"not-it\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        delete("/api/auth/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isNoContent());

        // The account (and its token) no longer works.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"delete-me@example.com","password":"secret123"}"""))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private String register(String email, String displayName) throws Exception {
        return registerFullSession(email, displayName).get("token").asText();
    }

    private JsonNode registerFullSession(String email, String displayName) throws Exception {
        MockHttpServletRequestBuilder req =
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                        {"email":"%s","password":"secret123","displayName":"%s"}"""
                                        .formatted(email, displayName));
        MvcResult result = mvc.perform(req).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createBoard(String token, String name) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/boards")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
