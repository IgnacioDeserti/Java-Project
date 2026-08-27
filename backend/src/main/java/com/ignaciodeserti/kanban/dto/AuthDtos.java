package com.ignaciodeserti.kanban.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password,
            @NotBlank @Size(max = 100) String displayName) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record VerifyEmailRequest(@NotBlank String token) {}

    public record ResendVerificationRequest(@Email @NotBlank String email) {}

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 6, message = "must be at least 6 characters")
                    String newPassword) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String displayName) {}

    // currentPassword/password are unvalidated here (not @NotBlank): an account created
    // via Google has no local password yet, so these flows accept a blank value from
    // such accounts and skip the current-password check server-side (see AuthService).
    public record ChangePasswordRequest(
            String currentPassword,
            @NotBlank @Size(min = 6, message = "must be at least 6 characters")
                    String newPassword) {}

    public record DeleteAccountRequest(String password) {}

    public record AuthResponse(
            String token,
            String refreshToken,
            String email,
            String displayName,
            boolean emailVerified,
            boolean hasPassword) {}

    /** Returned by /api/auth/me so the frontend can restore a session from a stored token. */
    public record UserResponse(
            String email, String displayName, boolean emailVerified, boolean hasPassword) {}

    /** Generic acknowledgement for flows that must not leak whether an email exists. */
    public record MessageResponse(String message) {}
}
