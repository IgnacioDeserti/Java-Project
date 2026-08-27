package com.ignaciodeserti.kanban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignaciodeserti.kanban.service.EmailService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Covers the flows that require verification to actually be enforced, unlike KanbanApiTest which
 * runs with require-email-verification=false for convenience.
 *
 * <p>Tokens are stored only as a SHA-256 hash (see UserTokenService), so tests recover the raw
 * token the same way a real user would: by reading it out of the email. Since no SMTP is
 * configured, EmailService logs it instead — this test attaches a Logback appender to capture that
 * line rather than trying to invert the hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.require-email-verification=true")
class AuthFlowsTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

    @Autowired MockMvc mvc;

    @Autowired ObjectMapper json;

    private ListAppender<ILoggingEvent> emailLog;

    @BeforeEach
    void captureEmailLog() {
        emailLog = new ListAppender<>();
        emailLog.start();
        ((Logger) LoggerFactory.getLogger(EmailService.class)).addAppender(emailLog);
    }

    @AfterEach
    void stopCapturingEmailLog() {
        ((Logger) LoggerFactory.getLogger(EmailService.class)).detachAppender(emailLog);
    }

    @Test
    void loginIsBlockedUntilEmailIsVerifiedThenWorksAfterVerifying() throws Exception {
        register("verify-me@example.com", "Verifier");

        // Unverified: login is refused with a distinguishable error code.
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"verify-me@example.com","password":"secret123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String rawToken = lastLoggedToken();

        mvc.perform(
                        post("/api/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        // Reusing the same (now revoked) link must not work a second time.
        mvc.perform(
                        post("/api/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"verify-me@example.com","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void refreshRotatesTheTokenAndLogoutRevokesIt() throws Exception {
        JsonNode session = register("refresh-me@example.com", "Refresher");
        String refreshToken = session.get("refreshToken").asText();

        JsonNode refreshed =
                body(
                        mvc.perform(
                                        post("/api/auth/refresh")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"refreshToken\":\""
                                                                + refreshToken
                                                                + "\"}"))
                                .andExpect(status().isOk())
                                .andReturn());

        String newRefreshToken = refreshed.get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // The old refresh token was single-use: it's now dead.
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + newRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordResetsAndRevokesAllSessions() throws Exception {
        JsonNode session = register("reset-me@example.com", "Resetter");
        String oldRefreshToken = session.get("refreshToken").asText();

        // Verify the account so the final login below is only exercising the new password,
        // not tripping over the separate "email not verified" gate.
        mvc.perform(
                        post("/api/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + lastLoggedToken() + "\"}"))
                .andExpect(status().isOk());

        // Doesn't leak whether the email exists either way.
        mvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"reset-me@example.com\"}"))
                .andExpect(status().isOk());
        String resetToken = lastLoggedToken();

        mvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"nobody-here@example.com\"}"))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + resetToken
                                                + "\",\"newPassword\":\"new-secret-456\"}"))
                .andExpect(status().isOk());

        // The pre-reset refresh token must no longer work: resetting a password logs out
        // every session, in case it was compromised.
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"reset-me@example.com","password":"new-secret-456"}"""))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private JsonNode register(String email, String displayName) throws Exception {
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
        return body(result);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String lastLoggedToken() {
        for (int i = emailLog.list.size() - 1; i >= 0; i--) {
            Matcher matcher = TOKEN_PATTERN.matcher(emailLog.list.get(i).getFormattedMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No email with a token was logged");
    }
}
