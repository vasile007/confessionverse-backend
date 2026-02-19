package com.confessionverse.backend;

import com.confessionverse.backend.model.*;
import com.confessionverse.backend.repository.ConfessionReportRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.reports.email-enabled=false")
@AutoConfigureMockMvc
@Transactional
class AdminReportModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private ConfessionReportRepository reportRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void adminCanListReports() throws Exception {
        User admin = createUser("admin-list", Role.ADMIN);
        User author = createUser("author-list", Role.USER);
        User reporter = createUser("reporter-list", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "for list"), reporter);

        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(report.getId().intValue())))
                .andExpect(jsonPath("$[*].status", hasItem("PENDING")))
                .andExpect(jsonPath("$[*].reporterEmail", hasItem(reporter.getEmail())));
    }

    @Test
    void adminCanResolveAndRejectReport() throws Exception {
        User admin = createUser("admin-status", Role.ADMIN);
        User author = createUser("author-status", Role.USER);
        User reporter = createUser("reporter-status", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "for status"), reporter);

        mockMvc.perform(put("/api/admin/reports/{id}/status", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Resolved after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(put("/api/admin/reports/{id}/status", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"adminNote\":\"Rejected after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void adminCannotResolveWithoutAdminNote() throws Exception {
        User admin = createUser("admin-status-no-note", Role.ADMIN);
        User author = createUser("author-status-no-note", Role.USER);
        User reporter = createUser("reporter-status-no-note", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "for status note"), reporter);

        mockMvc.perform(put("/api/admin/reports/{id}/status", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("adminNote is required for RESOLVED or REJECTED"));
    }

    @Test
    void adminFallbackRoutesAlsoWork() throws Exception {
        User admin = createUser("admin-fallback", Role.ADMIN);
        User author = createUser("author-fallback", Role.USER);
        User reporter = createUser("reporter-fallback", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "for fallback"), reporter);

        mockMvc.perform(put("/api/admin/reports/{id}", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Legacy status update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(post("/api/admin/reports/{id}/email", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"adminNote\":\"Legacy notify\",\"sendEmail\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailed").value(false))
                .andExpect(jsonPath("$.reason").value("EMAIL_UNAVAILABLE"));
    }

    @Test
    void userCannotUpdateReportStatus() throws Exception {
        User normalUser = createUser("normal-status", Role.USER);
        User author = createUser("author-status-forbidden", Role.USER);
        User reporter = createUser("reporter-status-forbidden", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "forbidden status"), reporter);

        mockMvc.perform(put("/api/admin/reports/{id}/status", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(normalUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Not allowed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void userCannotUpdateReportStatusThroughCompatibilityAlias() throws Exception {
        User normalUser = createUser("normal-status-alias", Role.USER);
        User author = createUser("author-status-alias", Role.USER);
        User reporter = createUser("reporter-status-alias", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "alias forbidden status"), reporter);

        mockMvc.perform(put("/api/reports/{id}", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(normalUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Not allowed\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void notifyReturnsFalseWhenEmailUnavailable() throws Exception {
        User admin = createUser("admin-notify-off", Role.ADMIN);
        User author = createUser("author-notify-off", Role.USER);
        User reporter = createUser("reporter-notify-off", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "notify off"), reporter);

        mockMvc.perform(post("/api/admin/reports/{id}/notify", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"done\",\"sendEmail\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailed").value(false))
                .andExpect(jsonPath("$.reason").value("EMAIL_UNAVAILABLE"));
    }

    @Test
    void missingReportReturns404() throws Exception {
        User admin = createUser("admin-missing-report", Role.ADMIN);

        mockMvc.perform(put("/api/admin/reports/{id}/status", 999999)
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Missing report\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Report not found with id 999999"));
    }

    @Test
    void missingReportNotifyReturns404() throws Exception {
        User admin = createUser("admin-missing-report-notify", Role.ADMIN);

        mockMvc.perform(post("/api/admin/reports/{id}/notify", 999999)
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"Missing report notify\",\"sendEmail\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Report not found with id 999999"));
    }

    private User createUser(String prefix, Role role) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + uid);
        user.setEmail(prefix + "-" + uid + "@test.local");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Confession createConfession(User author, String content) {
        Confession confession = new Confession();
        confession.setUser(author);
        confession.setContent(content);
        confession.setHidden(false);
        confession.setLikeCount(0);
        confession.setDislikeCount(0);
        return confessionRepository.save(confession);
    }

    private ConfessionReport createReport(Confession confession, User reporterUser) {
        ConfessionReport report = ConfessionReport.builder()
                .confession(confession)
                .reporterUser(reporterUser)
                .reporter(reporterUser)
                .reporterIp("127.0.0.1")
                .reason("Spam")
                .description("Test report")
                .severity("MEDIUM")
                .status(ReportStatus.PENDING)
                .build();
        return reportRepository.save(report);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
