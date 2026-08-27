package com.ignaciodeserti.kanban.security;

import com.ignaciodeserti.kanban.dto.AuthDtos.AuthResponse;
import com.ignaciodeserti.kanban.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Runs after Spring Security's OAuth2 login filter has already talked to Google and
 * verified the user. From here on this is just our own login: mint the same JWT +
 * refresh token pair a normal email/password login would get, and hand them to the
 * frontend.
 *
 * The frontend is a static SPA (no server-side session/cookie story), so the tokens are
 * passed back via a URL fragment rather than a query string or a redirect body: fragments
 * are never sent to the server or logged by proxies/access logs, only read client-side.
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        Boolean emailVerified = oauth2User.getAttribute("email_verified");

        AuthResponse session = authService.loginOrRegisterWithGoogle(email, name, Boolean.TRUE.equals(emailVerified));

        String fragment = "token=" + encode(session.token()) + "&refreshToken=" + encode(session.refreshToken());
        response.sendRedirect(frontendBaseUrl + "/oauth-callback#" + fragment);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
