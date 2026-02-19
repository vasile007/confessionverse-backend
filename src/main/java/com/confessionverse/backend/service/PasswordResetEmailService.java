package com.confessionverse.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.password-reset.email-from}")
    private String fromAddress;

    @Value("${app.password-reset.frontend-base-url}")
    private String frontendBaseUrl;

    public PasswordResetEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = buildResetUrl(token);
        String subject = "Reset your Confessionverse password";
        String textBody = "Use this link to reset your password: " + resetUrl
                + System.lineSeparator()
                + "This link expires soon.";
        String htmlBody = "<p>Use this link to reset your password:</p>"
                + "<p><a href=\"" + resetUrl + "\">Reset password</a></p>"
                + "<p>This link expires soon.</p>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not compose password reset email", ex);
        }
    }

    private String buildResetUrl(String token) {
        String baseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return String.format(Locale.ROOT, "%s/reset-password?token=%s", baseUrl, token);
    }
}
