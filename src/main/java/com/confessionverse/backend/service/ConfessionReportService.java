package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.requestDTO.ConfessionReportRequestDto;
import com.confessionverse.backend.dto.responseDTO.ReportNotifyResponseDTO;
import com.confessionverse.backend.exception.ReportAlreadyExistsException;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.ConfessionReport;
import com.confessionverse.backend.model.ReportStatus;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ConfessionReportRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.security.ownership.OwnableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ConfessionReportService implements OwnableService<ConfessionReport> {

    private static final Logger logger = LoggerFactory.getLogger(ConfessionReportService.class);

    private final ConfessionReportRepository reportRepository;
    private final ConfessionRepository confessionRepository;
    private final ReportEmailService reportEmailService;

    public ConfessionReportService(ConfessionReportRepository reportRepository,
                                   ConfessionRepository confessionRepository,
                                   ReportEmailService reportEmailService) {
        this.reportRepository = reportRepository;
        this.confessionRepository = confessionRepository;
        this.reportEmailService = reportEmailService;
    }

    @Transactional
    public String reportConfession(Long confessionId, User reporterUser, ConfessionReportRequestDto dto, String reporterIp) {
        Confession confession = confessionRepository.findById(confessionId)
                .orElseThrow(() -> new IllegalArgumentException("Confession not found"));

        if (reporterIp == null || reporterIp.isBlank()) {
            reporterIp = "unknown";
        }

        boolean alreadyReported = reportRepository.existsByConfessionIdAndReporterUser(confessionId, reporterUser);
        if (alreadyReported) {
            throw new ReportAlreadyExistsException("You have already reported this confession.");
        }

        String severity = dto.getSeverity();
        if (severity == null || severity.isBlank()) {
            severity = "MEDIUM";
        }

        ConfessionReport report = ConfessionReport.builder()
                .confession(confession)
                .reporterUser(reporterUser)
                .reporter(reporterUser)
                .reporterIp(reporterIp)
                .reason(dto.getReason())
                .description(dto.getDescription())
                .severity(severity)
                .build();

        logger.info("Saving report: confessionId={}, reporterUserId={}, severity={}, hasDescription={}",
                confessionId,
                reporterUser != null ? reporterUser.getId() : null,
                severity,
                dto.getDescription() != null && !dto.getDescription().isBlank());

        reportRepository.save(report);

        return "Report submitted successfully.";
    }



    public List<ConfessionReport> getReportsByConfessionId(Long confessionId) {
        return reportRepository.findAllByConfessionId(confessionId);
    }

    public List<ConfessionReport> getAllReports() {
        return reportRepository.findAll();
    }

    public List<ConfessionReport> getReportsForUser(User reporterUser) {
        return reportRepository.findAllByReporterUser(reporterUser);
    }

    @Transactional
    public ConfessionReport updateReportStatusByAdmin(Long reportId, String statusValue, String adminNote) {
        ConfessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id " + reportId));

        ReportStatus status = parseStatus(statusValue);
        validateAdminNote(status, adminNote);
        report.setStatus(status);
        if (adminNote != null && !adminNote.isBlank()) {
            logger.info("Admin moderation note for report {}: {}", reportId, adminNote.trim());
        }
        return reportRepository.save(report);
    }

    @Transactional
    public ReportNotifyResponseDTO notifyReportedUser(Long reportId, String statusValue, String adminNote, Boolean sendEmail) {
        ConfessionReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id " + reportId));

        ReportStatus status = parseStatus(statusValue);
        validateAdminNote(status, adminNote);
        report.setStatus(status);
        report = reportRepository.save(report);
        logger.info("Admin notify note for report {}: {}", reportId, adminNote.trim());

        String finalStatus = report.getStatus() != null ? report.getStatus().name() : ReportStatus.PENDING.name();
        boolean shouldSend = sendEmail == null || sendEmail;
        if (!shouldSend) {
            return new ReportNotifyResponseDTO(report.getId(), finalStatus, false, null, "Email sending skipped", "DISABLED_BY_REQUEST");
        }

        User reporterUser = report.getReporterUser();
        String recipientEmail = reporterUser != null ? reporterUser.getEmail() : null;
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return new ReportNotifyResponseDTO(report.getId(), finalStatus, false, "", "Reporter has no email", "NO_EMAIL");
        }

        try {
            reportEmailService.sendReportModerationEmail(recipientEmail, report.getId(), finalStatus, adminNote);
            return new ReportNotifyResponseDTO(report.getId(), finalStatus, true, recipientEmail, "Email sent", null);
        } catch (Exception ex) {
            logger.warn("Could not send report moderation email for report {}: {}", reportId, ex.getMessage());
            return new ReportNotifyResponseDTO(report.getId(), finalStatus, false, recipientEmail, "Email service unavailable", "EMAIL_UNAVAILABLE");
        }
    }

    private void validateAdminNote(ReportStatus status, String adminNote) {
        boolean requiresNote = status == ReportStatus.RESOLVED || status == ReportStatus.REJECTED;
        if (!requiresNote) {
            return;
        }
        if (adminNote == null || adminNote.isBlank()) {
            throw new IllegalArgumentException("adminNote is required for RESOLVED or REJECTED");
        }
    }

    private ReportStatus parseStatus(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            throw new IllegalArgumentException("Invalid status. Allowed: PENDING, RESOLVED, REJECTED");
        }
        try {
            return ReportStatus.valueOf(statusValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status. Allowed: PENDING, RESOLVED, REJECTED");
        }
    }

    @Override
    public Optional<ConfessionReport> getById(Long id) {
        return Optional.ofNullable(reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfessionReport not found")));
    }

    @Override
    public Class<ConfessionReport> getEntityClass() {
        return ConfessionReport.class;
    }
}



