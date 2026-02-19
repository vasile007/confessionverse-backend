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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.reports.email-enabled=true")
@AutoConfigureMockMvc
@Transactional
class AdminReportNotifyEmailEnabledIntegrationTest {

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
    void notifyReturnsEmailedTrueWhenServiceEnabled() throws Exception {
        User admin = createUser("admin-email-on", Role.ADMIN);
        User author = createUser("author-email-on", Role.USER);
        User reporter = createUser("reporter-email-on", Role.USER);
        ConfessionReport report = createReport(createConfession(author, "email on"), reporter);

        mockMvc.perform(post("/api/admin/reports/{id}/notify", report.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"reviewed\",\"sendEmail\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailed").value(true))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.recipientEmail").value(reporter.getEmail()));
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
                .reason("Harassment")
                .description("Enabled-email test report")
                .severity("HIGH")
                .status(ReportStatus.PENDING)
                .build();
        return reportRepository.save(report);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
