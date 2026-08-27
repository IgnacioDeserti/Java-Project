package com.ignaciodeserti.kanban.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        response.sendRedirect(frontendBaseUrl + "/oauth-callback#error=google_login_failed");
    }
}
