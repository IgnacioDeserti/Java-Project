package com.ignaciodeserti.kanban.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash, or null for an account that only ever signed in via Google. */
    private String password;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean emailVerified = false;

    /** How the account was created; informational — a Google account can add a local
     *  password later (via forgot-password), and this won't change to reflect that. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Board> boards = new ArrayList<>();

    public enum AuthProvider {
        LOCAL, GOOGLE
    }
}
