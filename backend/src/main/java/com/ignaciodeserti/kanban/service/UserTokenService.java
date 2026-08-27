package com.ignaciodeserti.kanban.service;

import com.ignaciodeserti.kanban.entity.User;
import com.ignaciodeserti.kanban.entity.UserToken;
import com.ignaciodeserti.kanban.entity.UserToken.Type;
import com.ignaciodeserti.kanban.repository.UserTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and redeems one-time tokens (email verification, password reset, refresh).
 * Callers only ever see the raw token; the database only ever stores its hash.
 */
@Service
@RequiredArgsConstructor
public class UserTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository userTokenRepository;

    /** Creates a new token, invalidating any earlier ones of the same type for this user. */
    public String issue(User user, Type type, Duration ttl) {
        userTokenRepository.deleteByUserAndType(user, type);

        String raw = randomToken();

        UserToken token = new UserToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setType(type);
        token.setExpiresAt(Instant.now().plus(ttl));
        userTokenRepository.save(token);

        return raw;
    }

    /** Looks up a raw token by its hash; the caller decides what "usable" means for its flow. */
    public Optional<UserToken> find(String rawToken, Type type) {
        return userTokenRepository.findByTokenHashAndType(hash(rawToken), type);
    }

    public void revoke(UserToken token) {
        token.setRevoked(true);
        userTokenRepository.save(token);
    }

    public void revokeAll(User user, Type type) {
        userTokenRepository.deleteByUserAndType(user, type);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
