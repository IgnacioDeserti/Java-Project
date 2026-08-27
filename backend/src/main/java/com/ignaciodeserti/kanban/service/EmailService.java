package com.ignaciodeserti.kanban.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification, password reset). When no SMTP host is configured — the
 * out-of-the-box local/dev setup — it logs the email instead of sending it, so the app is fully
 * usable without a mail provider. Plug in real SMTP credentials (MAIL_HOST / MAIL_USERNAME /
 * MAIL_PASSWORD) to send for real.
 */
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;
    private final boolean smtpConfigured;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.host:}") String host,
            @Value("${app.mail.from:no-reply@example.com}") String from,
            @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
        this.smtpConfigured = host != null && !host.isBlank();
    }

    public void sendVerificationEmail(String to, String rawToken) {
        String link = baseUrl + "/verify-email?token=" + rawToken;
        send(
                to,
                "Verify your email",
                "Welcome! Confirm your email address by opening this link:\n\n"
                        + link
                        + "\n\nThis link expires in 24 hours.");
    }

    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = baseUrl + "/reset-password?token=" + rawToken;
        send(
                to,
                "Reset your password",
                "We received a request to reset your password. Open this link to choose a new one:\n\n"
                        + link
                        + "\n\nIf you didn't request this, you can ignore this email. This link expires in 1 hour.");
    }

    private void send(String to, String subject, String body) {
        if (!smtpConfigured) {
            log.info("[DEV EMAIL] To: {} | Subject: {} | Body:\n{}", to, subject, body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            // A down mail provider shouldn't break registration/reset flows; the user can
            // always ask for the email to be resent once the issue is fixed.
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
