package com.ignaciodeserti.kanban.service;

import com.ignaciodeserti.kanban.config.EmailNotVerifiedException;
import com.ignaciodeserti.kanban.config.NotFoundException;
import com.ignaciodeserti.kanban.dto.AuthDtos.*;
import com.ignaciodeserti.kanban.entity.User;
import com.ignaciodeserti.kanban.entity.UserToken;
import com.ignaciodeserti.kanban.entity.UserToken.Type;
import com.ignaciodeserti.kanban.repository.UserRepository;
import com.ignaciodeserti.kanban.security.JwtService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserTokenService userTokenService;
    private final EmailService emailService;

    @Value("${app.jwt.refresh-expiration-ms:2592000000}") // 30 days default
    private long refreshExpirationMs;

    @Value("${app.security.require-email-verification:true}")
    private boolean requireEmailVerification;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        userRepository.save(user);

        String verificationToken =
                userTokenService.issue(user, Type.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
        emailService.sendVerificationEmail(user.getEmail(), verificationToken);

        // The new account can use the app right away; verification is only enforced on
        // future logins, so the first session isn't blocked on checking an inbox.
        return issueSession(user);
    }

    public UserResponse me(String email) {
        User user = getUser(email);
        return toUserResponse(user);
    }

    /**
     * Finds-or-creates a user for a successful Google sign-in. An existing local account with the
     * same email is linked (not duplicated) and, since Google already verified the address, marked
     * verified too. New accounts get no local password — they can add one later via
     * forgot-password, which needs no old password to work.
     */
    @Transactional
    public AuthResponse loginOrRegisterWithGoogle(
            String email, String displayName, boolean googleVerifiedEmail) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseGet(
                                () -> {
                                    User created = new User();
                                    created.setEmail(email);
                                    created.setDisplayName(
                                            displayName != null && !displayName.isBlank()
                                                    ? displayName
                                                    : email);
                                    created.setAuthProvider(User.AuthProvider.GOOGLE);
                                    created.setEmailVerified(googleVerifiedEmail);
                                    return userRepository.save(created);
                                });

        if (googleVerifiedEmail && !user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return issueSession(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = getUser(request.email());

        if (requireEmailVerification && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        return issueSession(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        UserToken token =
                userTokenService
                        .find(request.refreshToken(), Type.REFRESH)
                        .filter(UserToken::isUsable)
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "Invalid or expired refresh token"));

        userTokenService.revoke(token); // rotate: each refresh token is single-use
        return issueSession(token.getUser());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        // Best-effort: an already-invalid token means the session is over either way.
        userTokenService
                .find(request.refreshToken(), Type.REFRESH)
                .ifPresent(userTokenService::revoke);
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        UserToken token =
                userTokenService
                        .find(request.token(), Type.EMAIL_VERIFICATION)
                        .filter(UserToken::isUsable)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Invalid or expired verification link"));

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        userTokenService.revoke(token);

        return new MessageResponse("Email verified — you can now log in");
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        userRepository
                .findByEmail(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(
                        user -> {
                            String token =
                                    userTokenService.issue(
                                            user, Type.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
                            emailService.sendVerificationEmail(user.getEmail(), token);
                        });

        // Same response whether or not the account exists / is already verified, so this
        // endpoint can't be used to probe which emails are registered.
        return new MessageResponse("If that account needs verifying, we've sent a new email");
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository
                .findByEmail(request.email())
                .ifPresent(
                        user -> {
                            String token =
                                    userTokenService.issue(
                                            user, Type.PASSWORD_RESET, PASSWORD_RESET_TTL);
                            emailService.sendPasswordResetEmail(user.getEmail(), token);
                        });

        return new MessageResponse("If that email is registered, we've sent a reset link");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        UserToken token =
                userTokenService
                        .find(request.token(), Type.PASSWORD_RESET)
                        .filter(UserToken::isUsable)
                        .orElseThrow(() -> new NotFoundException("Invalid or expired reset link"));

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        userTokenService.revoke(token);

        // A password reset invalidates every existing session, in case the old password
        // (and any live tokens) were compromised.
        userTokenService.revokeAll(user, Type.REFRESH);

        return new MessageResponse("Password updated — please log in again");
    }

    @Transactional
    public UserResponse updateProfile(String userEmail, UpdateProfileRequest request) {
        User user = getUser(userEmail);
        user.setDisplayName(request.displayName());
        userRepository.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public MessageResponse changePassword(String userEmail, ChangePasswordRequest request) {
        User user = getUser(userEmail);

        // A Google-only account has no password to check yet — this call sets its first
        // one. An account that already has one must prove it before changing it.
        if (user.getPassword() != null
                && !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Same as a password reset: every other session is logged out, since the old
        // password (and any tokens issued under it) may no longer be trustworthy.
        userTokenService.revokeAll(user, Type.REFRESH);

        return new MessageResponse("Password updated");
    }

    @Transactional
    public void deleteAccount(String userEmail, DeleteAccountRequest request) {
        User user = getUser(userEmail);

        // A Google-only account has no password to confirm with — the JWT itself (short-
        // lived, and already required to reach this endpoint) is the confirmation.
        if (user.getPassword() != null
                && !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Password is incorrect");
        }

        // Cascades to the user's boards/columns/cards (JPA) and tokens (DB FK cascade).
        userRepository.delete(user);
    }

    private AuthResponse issueSession(User user) {
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken =
                userTokenService.issue(user, Type.REFRESH, Duration.ofMillis(refreshExpirationMs));
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified(),
                user.getPassword() != null);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified(),
                user.getPassword() != null);
    }

    private User getUser(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
