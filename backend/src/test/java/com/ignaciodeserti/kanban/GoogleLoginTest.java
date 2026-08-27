package com.ignaciodeserti.kanban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignaciodeserti.kanban.dto.AuthDtos.AuthResponse;
import com.ignaciodeserti.kanban.entity.User;
import com.ignaciodeserti.kanban.repository.UserRepository;
import com.ignaciodeserti.kanban.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The real OAuth2 redirect dance (browser -> Google -> callback) needs a live Google app and isn't
 * practical to exercise in CI, so these tests go straight at AuthService.loginOrRegisterWithGoogle
 * — the part GoogleOAuthSuccessHandler calls once Spring Security has already done the OAuth2
 * handshake and verified the identity. What's under test here is what happens *after* that point:
 * account creation/linking, and the password-optional account flows a Google-only sign-up creates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleLoginTest {

    @Autowired AuthService authService;

    @Autowired UserRepository userRepository;

    @Autowired MockMvc mvc;

    @Autowired ObjectMapper json;

    @Test
    void firstGoogleSignInCreatesAVerifiedPasswordlessAccount() {
        AuthResponse session =
                authService.loginOrRegisterWithGoogle("newbie@example.com", "Newbie", true);

        assertThat(session.hasPassword()).isFalse();
        assertThat(session.emailVerified()).isTrue();

        User user = userRepository.findByEmail("newbie@example.com").orElseThrow();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getDisplayName()).isEqualTo("Newbie");
    }

    @Test
    void secondGoogleSignInReusesTheSameAccount() {
        authService.loginOrRegisterWithGoogle("repeat@example.com", "Repeat", true);
        long afterFirst = userRepository.count();

        AuthResponse second =
                authService.loginOrRegisterWithGoogle("repeat@example.com", "Repeat", true);

        assertThat(userRepository.count()).isEqualTo(afterFirst); // no duplicate account
        assertThat(second.email()).isEqualTo("repeat@example.com");
    }

    @Test
    void signingInWithGoogleVerifiesAnExistingUnverifiedLocalAccount() throws Exception {
        register("local-first@example.com", "Local First");
        assertThat(
                        userRepository
                                .findByEmail("local-first@example.com")
                                .orElseThrow()
                                .isEmailVerified())
                .isFalse();

        authService.loginOrRegisterWithGoogle("local-first@example.com", "Local First", true);

        User user = userRepository.findByEmail("local-first@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPassword()).isNotNull(); // the original local password is untouched
        assertThat(user.getAuthProvider())
                .isEqualTo(User.AuthProvider.LOCAL); // provider reflects signup origin
    }

    @Test
    void aPasswordlessAccountCanSetItsFirstPasswordWithoutProvingAnOldOne() throws Exception {
        AuthResponse session =
                authService.loginOrRegisterWithGoogle("set-pw@example.com", "Set PW", true);

        mvc.perform(
                        post("/api/auth/change-password")
                                .header("Authorization", "Bearer " + session.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"\",\"newPassword\":\"brand-new-123\"}"))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"set-pw@example.com","password":"brand-new-123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    void aPasswordlessAccountCanDeleteItselfWithoutAPassword() throws Exception {
        AuthResponse session =
                authService.loginOrRegisterWithGoogle("delete-me-google@example.com", "Bye", true);

        mvc.perform(
                        delete("/api/auth/me")
                                .header("Authorization", "Bearer " + session.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("delete-me-google@example.com")).isEmpty();
    }

    private void register(String email, String displayName) throws Exception {
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
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("hasPassword").asBoolean()).isTrue();
    }
}
