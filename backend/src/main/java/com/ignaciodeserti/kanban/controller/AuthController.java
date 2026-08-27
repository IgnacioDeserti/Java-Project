package com.ignaciodeserti.kanban.controller;

import com.ignaciodeserti.kanban.dto.AuthDtos.*;
import com.ignaciodeserti.kanban.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return new MessageResponse("Logged out");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendVerification(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    /** Authenticated: lets the frontend check a stored token is still good on page load. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserDetails user) {
        return authService.me(user.getUsername());
    }

    @PutMapping("/me")
    public UserResponse updateProfile(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(user.getUsername(), request);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(user.getUsername(), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(user.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}
