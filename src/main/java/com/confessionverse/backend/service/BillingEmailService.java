package com.confessionverse.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class BillingEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.billing.email-from}")
    private String fromAddress;

    @Value("${app.billing.email-fallback-enabled:true}")
    private boolean fallbackEmailEnabled;

    public BillingEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInvoicePaidConfirmation(String toEmail,
                                            String planType,
                                            Long amountCents,
                                            String currency,
                                            LocalDateTime paidAt,
                                            String invoiceId,
                                            String hostedInvoiceUrl) {
        if (!fallbackEmailEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }

        String amount = formatAmount(amountCents, currency);
        String subject = "Payment confirmed";
        String textBody = "Your " + planType + " payment was confirmed."
                + System.lineSeparator()
                + "Amount: " + amount
                + System.lineSeparator()
                + "Date: " + paidAt
                + System.lineSeparator()
                + "Invoice ID: " + invoiceId
                + System.lineSeparator()
                + "Invoice link: " + (hostedInvoiceUrl == null ? "N/A" : hostedInvoiceUrl);

        send(toEmail, subject, textBody);
    }

    public void sendInvoicePaymentFailedWarning(String toEmail,
                                                String planType,
                                                String invoiceId,
                                                String hostedInvoiceUrl) {
        if (!fallbackEmailEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }

        String subject = "Payment failed";
        String textBody = "Your " + planType + " payment failed."
                + System.lineSeparator()
                + "Invoice ID: " + invoiceId
                + System.lineSeparator()
                + "Invoice link: " + (hostedInvoiceUrl == null ? "N/A" : hostedInvoiceUrl);

        send(toEmail, subject, textBody);
    }

    private void send(String toEmail, String subject, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            mailSender.send(message);
        } catch (MessagingException ex) {
            // Keep webhook flow non-blocking on email failures.
        }
    }

    private String formatAmount(Long amountCents, String currency) {
        if (amountCents == null || currency == null || currency.isBlank()) {
            return "N/A";
        }
        BigDecimal value = BigDecimal.valueOf(amountCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return value + " " + currency.toUpperCase();
    }
}
