package com.ignaciodeserti.kanban.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time-use, opaque token handed to the user (via email or as a refresh token),
 * with only its SHA-256 hash stored here — so a leaked database dump doesn't hand out
 * usable tokens. Used for email verification, password reset, and refresh tokens alike.
 */
@Entity
@Table(name = "user_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public enum Type {
        EMAIL_VERIFICATION, PASSWORD_RESET, REFRESH
    }
}
