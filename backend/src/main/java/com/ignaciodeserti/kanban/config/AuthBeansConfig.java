package com.ignaciodeserti.kanban.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of SecurityConfig: AuthService needs both of these beans, and SecurityConfig now
 * depends (transitively, via the Google OAuth success handler) on AuthService — a @Bean method
 * living on SecurityConfig itself would be a circular dependency (building SecurityConfig would
 * require SecurityConfig to already exist, to invoke the method on).
 *
 * <p>The AuthenticationManager is built directly from a DaoAuthenticationProvider here, rather than
 * via Spring Security's AuthenticationConfiguration#getAuthenticationManager — that path scans the
 * context for AuthenticationProvider/UserDetailsService beans, which would reach back into
 * SecurityConfig and reintroduce the same cycle.
 */
@Configuration
@RequiredArgsConstructor
public class AuthBeansConfig {

    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
