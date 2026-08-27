package com.ignaciodeserti.kanban.security;

import com.ignaciodeserti.kanban.entity.User;
import com.ignaciodeserti.kanban.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    // A real, well-formed bcrypt hash of a random value nobody knows — used as a stand-in
    // for accounts with no local password (Google-only sign-ups). It can never match any
    // password a user submits, but keeps Spring Security's User builder (which rejects a
    // null password) happy. Computed once per JVM, not per request — bcrypt is deliberately
    // slow, and this runs on every authenticated request via JwtAuthFilter.
    private static final String NO_PASSWORD_SET =
            new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () -> new UsernameNotFoundException("User not found: " + email));

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : NO_PASSWORD_SET)
                .authorities("USER")
                .build();
    }
}
