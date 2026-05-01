package com.confessionverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReportEmailService {

    private final EmailService emailService;

    @Value("${app.reports.email-enabled:false}")
    private boolean emailEnabled;

    public ReportEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendReportModerationEmail(String toEmail, Long reportId, String status, String note) {
        if (!emailEnabled) {
            throw new IllegalStateException("EMAIL_UNAVAILABLE");
        }
        if (!StringUtils.hasText(toEmail)) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        String subject = "Update on your Confessionverse report #" + reportId;
        emailService.sendHtmlEmail(toEmail, subject, buildReportModerationHtml(reportId, status, note));
    }

    private String buildReportModerationHtml(Long reportId, String status, String note) {
        String safeStatus = StringUtils.hasText(status) ? status.trim() : "PENDING";
        String explanation = StringUtils.hasText(note)
                ? note.trim()
                : "Our moderation team reviewed the report and updated its status.";

        return """
                <html>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;color:#111827;">
                  <div style="width:100%%;padding:24px 12px;background-color:#f4f7fb;">
                    <div style="max-width:600px;margin:0 auto;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:20px;overflow:hidden;">
                      <div style="padding:32px 32px 12px 32px;">
                        <div style="font-size:24px;font-weight:700;color:#0f172a;">ConfessionVerse</div>
                        <div style="font-size:14px;color:#64748b;margin-top:6px;">Moderation update</div>
                      </div>
                      <div style="padding:12px 32px 32px 32px;">
                        <h1 style="margin:0 0 16px 0;font-size:28px;line-height:36px;color:#111827;">Your report has been reviewed</h1>
                        <div style="background-color:#f8fafc;border:1px solid #e5e7eb;border-radius:14px;padding:16px;margin-bottom:18px;">
                          <p style="margin:0 0 8px 0;font-size:14px;line-height:22px;color:#475569;"><strong>Report ID:</strong> %s</p>
                          <p style="margin:0;font-size:14px;line-height:22px;color:#475569;"><strong>Status:</strong> %s</p>
                        </div>
                        <p style="margin:0 0 18px 0;font-size:15px;line-height:24px;color:#374151;">%s</p>
                        <div style="margin:24px 0;">
                          <span style="display:inline-block;background-color:#111827;color:#ffffff;padding:14px 24px;border-radius:12px;font-size:15px;font-weight:600;">
                            Status updated
                          </span>
                        </div>
                        <p style="margin:0;font-size:14px;line-height:22px;color:#64748b;">
                          Thank you for helping keep ConfessionVerse safe and respectful.
                        </p>
                      </div>
                      <div style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e5e7eb;font-size:13px;line-height:20px;color:#64748b;">
                        ConfessionVerse
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escapeHtml(String.valueOf(reportId)), escapeHtml(safeStatus), escapeHtml(explanation));
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
