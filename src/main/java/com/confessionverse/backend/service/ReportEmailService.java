package com.confessionverse.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportEmailService {

    @Value("${app.reports.email-enabled:false}")
    private boolean emailEnabled;

    public void sendReportModerationEmail(String toEmail, Long reportId, String status, String note) {
        if (!emailEnabled) {
            throw new IllegalStateException("EMAIL_UNAVAILABLE");
        }
        // Placeholder implementation. In production, replace with real SMTP/provider integration.
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }
    }
}
