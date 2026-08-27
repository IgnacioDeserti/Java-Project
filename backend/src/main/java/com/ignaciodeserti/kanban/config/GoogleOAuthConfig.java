package com.ignaciodeserti.kanban.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Builds the Google OAuth2 client registration by hand, from plain env vars, instead of the usual
 * spring.security.oauth2.client.registration.* properties. Those properties fail application
 * startup if client-id/secret are blank — which they are by default, since "Sign in with Google" is
 * optional and off until the operator configures real credentials. This keeps every other
 * environment (tests, a fresh clone) working with zero Google setup, and only "activates" Google
 * login once real values are supplied.
 */
@Configuration
public class GoogleOAuthConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return registrationId -> null;
        }

        ClientRegistration google =
                CommonOAuth2Provider.GOOGLE
                        .getBuilder("google")
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .scope("openid", "email", "profile")
                        .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
