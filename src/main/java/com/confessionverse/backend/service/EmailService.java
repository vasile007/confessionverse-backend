package com.confessionverse.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final JavaMailSender mailSender;

    @Value("${SMTP_FROM}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPlainTextEmail(String to, String subject, String body) {
        validateInputs(to, subject, body);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(requireFromAddress());
        message.setTo(to.trim());
        message.setSubject(subject.trim());
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Sent plain text email to={} subject={}", maskEmail(to), sanitizeSubject(subject));
        } catch (MailException ex) {
            log.warn("Failed to send plain text email to={} subject={} reason={}",
                    maskEmail(to), sanitizeSubject(subject), ex.getMessage());
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        validateInputs(to, subject, htmlBody);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(requireFromAddress());
            helper.setTo(to.trim());
            helper.setSubject(subject.trim());
            helper.setText(toPlainText(htmlBody), htmlBody);
            mailSender.send(message);
            log.info("Sent HTML email to={} subject={}", maskEmail(to), sanitizeSubject(subject));
        } catch (MessagingException | MailException ex) {
            log.warn("Failed to send HTML email to={} subject={} reason={}",
                    maskEmail(to), sanitizeSubject(subject), ex.getMessage());
            throw new IllegalStateException("Failed to send email", ex);
        }
    }

    public void sendSimpleEmail(String to, String subject, String body) {
        sendPlainTextEmail(to, subject, body);
    }

    public void sendTestEmail(String to) {
        sendHtmlEmail(
                to,
                "Confessionverse email test",
                "<html><body style=\"margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;\">"
                        + "<div style=\"max-width:600px;margin:24px auto;padding:24px;\">"
                        + "<div style=\"background:#ffffff;border-radius:16px;padding:32px;border:1px solid #e5e7eb;\">"
                        + "<div style=\"font-size:24px;font-weight:700;color:#111827;margin-bottom:16px;\">ConfessionVerse</div>"
                        + "<div style=\"font-size:16px;line-height:24px;color:#374151;\">"
                        + "This is a test email from the Confessionverse backend SMTP configuration."
                        + "</div></div></div></body></html>"
        );
    }

    private String requireFromAddress() {
        if (!StringUtils.hasText(fromAddress)) {
            throw new IllegalStateException("SMTP_FROM is not configured");
        }
        return fromAddress.trim();
    }

    private void validateInputs(String to, String subject, String body) {
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (!StringUtils.hasText(subject)) {
            throw new IllegalArgumentException("Email subject is required");
        }
        if (body == null) {
            throw new IllegalArgumentException("Email body is required");
        }
    }

    private String toPlainText(String html) {
        String normalized = html.replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("</p>", "\n\n")
                .replace("</div>", "\n")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
        return HTML_TAG_PATTERN.matcher(normalized).replaceAll("").trim();
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "unknown";
        }
        String[] parts = email.trim().split("@", 2);
        String local = parts[0];
        String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
        return maskedLocal + "@" + parts[1];
    }

    private String sanitizeSubject(String subject) {
        return subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ").trim();
    }

}
