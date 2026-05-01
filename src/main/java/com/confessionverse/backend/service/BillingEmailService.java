package com.confessionverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class BillingEmailService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT);

    private final EmailService emailService;

    @Value("${app.billing.email-fallback-enabled:true}")
    private boolean fallbackEmailEnabled;

    public BillingEmailService(EmailService emailService) {
        this.emailService = emailService;
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
        sendHtml(toEmail, subject, buildPaymentSuccessHtml(planType, amount, paidAt, invoiceId, hostedInvoiceUrl));
    }

    public void sendInvoicePaymentFailedWarning(String toEmail,
                                                String planType,
                                                String invoiceId,
                                                String hostedInvoiceUrl) {
        if (!fallbackEmailEnabled || toEmail == null || toEmail.isBlank()) {
            return;
        }

        String subject = "Payment failed";
        sendHtml(toEmail, subject, buildPaymentIssueHtml(planType, invoiceId, hostedInvoiceUrl));
    }

    public void sendSubscriptionActiveEmail(String toEmail, String planType, LocalDateTime renewalAt, String manageBillingUrl) {
        if (!fallbackEmailEnabled || !StringUtils.hasText(toEmail)) {
            return;
        }
        sendHtml(toEmail, "Your subscription is active",
                buildSubscriptionActiveHtml(planType, renewalAt, manageBillingUrl));
    }

    public void sendSubscriptionCancelledEmail(String toEmail, String planType, LocalDateTime accessUntil, String manageBillingUrl) {
        if (!fallbackEmailEnabled || !StringUtils.hasText(toEmail)) {
            return;
        }
        sendHtml(toEmail, "Your subscription has been cancelled",
                buildSubscriptionCancelledHtml(planType, accessUntil, manageBillingUrl));
    }

    private void sendHtml(String toEmail, String subject, String htmlBody) {
        try {
            emailService.sendHtmlEmail(toEmail, subject, htmlBody);
        } catch (IllegalStateException ex) {
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

    private String buildPaymentSuccessHtml(String planType,
                                           String amount,
                                           LocalDateTime paidAt,
                                           String invoiceId,
                                           String hostedInvoiceUrl) {
        return buildBillingTemplate(
                "Payment confirmed",
                "Your " + defaultPlan(planType) + " payment was successful.",
                "Status: Paid",
                "View invoice",
                hostedInvoiceUrl,
                """
                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Your subscription remains active. No card details are included in this email.</p>
                        %s
                        """.formatted(buildFacts(
                        fact("Plan", defaultPlan(planType)),
                        fact("Amount", amount),
                        fact("Paid at", formatDate(paidAt)),
                        fact("Invoice ID", invoiceId)
                ))
        );
    }

    private String buildPaymentIssueHtml(String planType, String invoiceId, String hostedInvoiceUrl) {
        return buildBillingTemplate(
                "Payment issue detected",
                "We could not process your latest " + defaultPlan(planType) + " payment.",
                "Status: Payment failed",
                "Review billing",
                hostedInvoiceUrl,
                """
                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Please review your billing details to avoid interruption. No sensitive card details are included here.</p>
                        %s
                        <p style="margin:16px 0 0 0;font-size:14px;line-height:22px;color:#64748b;">If the issue persists, contact support through the billing area.</p>
                        """.formatted(buildFacts(
                        fact("Plan", defaultPlan(planType)),
                        fact("Invoice ID", invoiceId),
                        fact("Billing status", "Payment action required")
                ))
        );
    }

    private String buildSubscriptionActiveHtml(String planType, LocalDateTime renewalAt, String manageBillingUrl) {
        return buildBillingTemplate(
                "Subscription active",
                "Your " + defaultPlan(planType) + " subscription is active.",
                "Status: Active",
                "Manage subscription",
                manageBillingUrl,
                """
                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">Your access to paid features is enabled.</p>
                        %s
                        <p style="margin:16px 0 0 0;font-size:14px;line-height:22px;color:#64748b;">You can manage or review billing at any time from your account.</p>
                        """.formatted(buildFacts(
                        fact("Plan", defaultPlan(planType)),
                        fact("Subscription status", "Active"),
                        fact("Next renewal", formatDate(renewalAt))
                ))
        );
    }

    private String buildSubscriptionCancelledHtml(String planType, LocalDateTime accessUntil, String manageBillingUrl) {
        return buildBillingTemplate(
                "Subscription cancelled",
                "Your " + defaultPlan(planType) + " subscription has been cancelled.",
                "Status: Cancelled",
                "Review billing",
                manageBillingUrl,
                """
                        <p style="margin:0 0 12px 0;font-size:15px;line-height:24px;color:#374151;">You may still have access until the end of the current billing period.</p>
                        %s
                        <p style="margin:16px 0 0 0;font-size:14px;line-height:22px;color:#64748b;">If this was not expected, review your billing settings.</p>
                        """.formatted(buildFacts(
                        fact("Plan", defaultPlan(planType)),
                        fact("Subscription status", "Cancelled"),
                        fact("Access until", formatDate(accessUntil))
                ))
        );
    }

    private String buildBillingTemplate(String title,
                                        String message,
                                        String badge,
                                        String ctaLabel,
                                        String ctaUrl,
                                        String bodyHtml) {
        String safeCtaUrl = StringUtils.hasText(ctaUrl) ? escapeHtml(ctaUrl) : null;
        String ctaBlock = safeCtaUrl == null ? "" : """
                <div style="margin:24px 0 18px 0;">
                  <a href="%s" style="display:inline-block;background-color:#111827;color:#ffffff;text-decoration:none;padding:14px 24px;border-radius:12px;font-size:16px;font-weight:600;">
                    %s
                  </a>
                </div>
                <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;color:#64748b;word-break:break-word;">
                  If the button does not work, use this link:
                  <a href="%s" style="color:#2563eb;text-decoration:underline;">%s</a>
                </p>
                """.formatted(safeCtaUrl, escapeHtml(ctaLabel), safeCtaUrl, safeCtaUrl);

        return """
                <html>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;color:#111827;">
                  <div style="width:100%%;padding:24px 12px;background-color:#f4f7fb;">
                    <div style="max-width:600px;margin:0 auto;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:20px;overflow:hidden;">
                      <div style="padding:32px 32px 12px 32px;">
                        <div style="font-size:24px;font-weight:700;color:#0f172a;">ConfessionVerse</div>
                        <div style="font-size:14px;color:#64748b;margin-top:6px;">Billing update</div>
                      </div>
                      <div style="padding:12px 32px 32px 32px;">
                        <div style="display:inline-block;background-color:#eff6ff;color:#1d4ed8;padding:8px 12px;border-radius:999px;font-size:12px;font-weight:700;margin-bottom:16px;">%s</div>
                        <h1 style="margin:0 0 16px 0;font-size:28px;line-height:36px;color:#111827;">%s</h1>
                        <p style="margin:0 0 16px 0;font-size:16px;line-height:26px;color:#374151;">%s</p>
                        %s
                        %s
                        <p style="margin:18px 0 0 0;font-size:13px;line-height:20px;color:#64748b;">ConfessionVerse</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(badge),
                escapeHtml(title),
                escapeHtml(message),
                ctaBlock,
                bodyHtml
        );
    }

    private String buildFacts(String... facts) {
        StringBuilder builder = new StringBuilder("""
                <div style="background-color:#f8fafc;border:1px solid #e5e7eb;border-radius:14px;padding:16px;">
                """);
        for (String fact : facts) {
            if (fact != null && !fact.isBlank()) {
                builder.append(fact);
            }
        }
        builder.append("</div>");
        return builder.toString();
    }

    private String fact(String label, String value) {
        if (!StringUtils.hasText(value) || "N/A".equals(value)) {
            return "";
        }
        return """
                <p style="margin:0 0 8px 0;font-size:14px;line-height:22px;color:#475569;">
                  <strong>%s:</strong> %s
                </p>
                """.formatted(escapeHtml(label), escapeHtml(value));
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "N/A" : value.format(DATE_FORMAT);
    }

    private String defaultPlan(String planType) {
        return StringUtils.hasText(planType) ? planType.trim().toUpperCase(Locale.ROOT) : "subscription";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
