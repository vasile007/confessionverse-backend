package com.confessionverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class PasswordResetEmailService {

    private static final String BRAND_NAME = "ConfessionVerse";

    private final EmailService emailService;

    @Value("${app.password-reset.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.password-reset.token-ttl-minutes:60}")
    private long tokenTtlMinutes;

    public PasswordResetEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = buildResetUrl(token);
        String subject = "Reset your Confessionverse password";
        emailService.sendHtmlEmail(toEmail, subject, buildPasswordResetHtml(resetUrl));
    }

    private String buildResetUrl(String token) {
        if (!StringUtils.hasText(frontendBaseUrl)) {
            throw new IllegalStateException("app.password-reset.frontend-base-url is not configured");
        }
        String baseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        return String.format(Locale.ROOT, "%s/reset-password?token=%s", baseUrl, token);
    }

    private String buildPasswordResetHtml(String resetUrl) {
        String expiryText = tokenTtlMinutes > 0 ? tokenTtlMinutes + " minutes" : "a limited time";
        return """
                <html>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;color:#111827;">
                  <div style="width:100%%;background-color:#f4f7fb;padding:24px 12px;">
                    <div style="max-width:600px;margin:0 auto;background-color:#ffffff;border:1px solid #e5e7eb;border-radius:20px;overflow:hidden;">
                      <div style="padding:32px 32px 12px 32px;">
                        <div style="font-size:24px;font-weight:700;color:#0f172a;">%s</div>
                        <div style="font-size:14px;color:#64748b;margin-top:6px;">Secure account access</div>
                      </div>
                      <div style="padding:12px 32px 32px 32px;">
                        <h1 style="margin:0 0 16px 0;font-size:28px;line-height:36px;color:#111827;">Reset your password</h1>
                        <p style="margin:0 0 16px 0;font-size:16px;line-height:26px;color:#374151;">
                          We received a request to reset the password for your %s account.
                        </p>
                        <div style="margin:28px 0;">
                          <a href="%s" style="display:inline-block;background-color:#111827;color:#ffffff;text-decoration:none;padding:14px 24px;border-radius:12px;font-size:16px;font-weight:600;">
                            Reset password
                          </a>
                        </div>
                        <p style="margin:0 0 14px 0;font-size:15px;line-height:24px;color:#374151;">
                          This link expires in %s.
                        </p>
                        <p style="margin:0 0 8px 0;font-size:14px;line-height:22px;color:#64748b;">If the button does not work, use this link:</p>
                        <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;word-break:break-word;">
                          <a href="%s" style="color:#2563eb;text-decoration:underline;">%s</a>
                        </p>
                        <div style="background-color:#fff7ed;border:1px solid #fdba74;border-radius:14px;padding:16px;">
                          <p style="margin:0;font-size:14px;line-height:22px;color:#9a3412;">
                            If you did not request this change, you can safely ignore this email. Your password will remain unchanged.
                          </p>
                        </div>
                      </div>
                      <div style="padding:20px 32px;background-color:#f8fafc;border-top:1px solid #e5e7eb;font-size:13px;line-height:20px;color:#64748b;">
                        %s
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(BRAND_NAME, BRAND_NAME, escapeHtml(resetUrl), escapeHtml(expiryText), escapeHtml(resetUrl), escapeHtml(resetUrl), BRAND_NAME);
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
