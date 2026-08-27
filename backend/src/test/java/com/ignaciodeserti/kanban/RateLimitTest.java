package com.ignaciodeserti.kanban;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The shared test suite runs with generous rate limits (see application-test.yml) so its many
 * registrations/logins from one IP never trip them. This class overrides the login limit down to
 * something a single test can exhaust, to prove the throttle actually fires.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.rate-limit.login.max-attempts=3",
            "app.rate-limit.login.window-seconds=60"
        })
class RateLimitTest {

    @Autowired MockMvc mvc;

    @Test
    void tooManyLoginAttemptsGetThrottled() throws Exception {
        String badLogin =
                """
                {"email":"nobody@example.com","password":"wrong-password"}""";

        for (int i = 0; i < 3; i++) {
            mvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(badLogin))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badLogin))
                .andExpect(status().isTooManyRequests());
    }
}
