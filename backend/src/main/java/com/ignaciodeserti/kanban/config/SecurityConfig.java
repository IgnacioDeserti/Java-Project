package com.ignaciodeserti.kanban.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignaciodeserti.kanban.security.GoogleOAuthFailureHandler;
import com.ignaciodeserti.kanban.security.GoogleOAuthSuccessHandler;
import com.ignaciodeserti.kanban.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;
    private final GoogleOAuthSuccessHandler googleOAuthSuccessHandler;
    private final GoogleOAuthFailureHandler googleOAuthFailureHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                            "/api/auth/verify-email", "/api/auth/resend-verification",
                            "/api/auth/forgot-password", "/api/auth/reset-password"
                    ).permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    // The SockJS handshake (plain HTTP polling/upgrade) has no JWT header to
                    // check; STOMP frames are authenticated separately, per-frame, by
                    // StompAuthChannelInterceptor once the WebSocket session is established.
                    .requestMatchers("/ws/**").permitAll()
                    // Spring Security's own OAuth2 login endpoints: initiating the Google
                    // redirect and receiving its callback. If Google isn't configured (see
                    // GoogleOAuthConfig), these simply have nothing to resolve and 404.
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Without these, an anonymous request to a protected route answers 403;
            // the frontend needs a 401 to know it should send the user back to login.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                    .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            .authenticationManager(authenticationManager)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                    .successHandler(googleOAuthSuccessHandler)
                    .failureHandler(googleOAuthFailureHandler)
            );

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, ex) -> writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, ex) -> writeError(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
