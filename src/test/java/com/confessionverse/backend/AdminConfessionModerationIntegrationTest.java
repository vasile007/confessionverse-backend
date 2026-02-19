package com.confessionverse.backend;

import com.confessionverse.backend.model.*;
import com.confessionverse.backend.repository.BoostRepository;
import com.confessionverse.backend.repository.ConfessionReportRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.ConfessionVoteRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminConfessionModerationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private BoostRepository boostRepository;

    @Autowired
    private ConfessionVoteRepository confessionVoteRepository;

    @Autowired
    private ConfessionReportRepository confessionReportRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void adminCanDeleteConfession() throws Exception {
        User admin = createUser("admin-delete", Role.ADMIN);
        User author = createUser("author-delete", Role.USER);
        Confession confession = createConfession(author, "delete target");

        mockMvc.perform(delete("/api/admin/confessions/{id}", confession.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNoContent());

        assertFalse(confessionRepository.existsById(confession.getId()));
    }

    @Test
    void adminCanHideConfession() throws Exception {
        User admin = createUser("admin-hide", Role.ADMIN);
        User author = createUser("author-hide", Role.USER);
        Confession confession = createConfession(author, "hide target");

        mockMvc.perform(patch("/api/admin/confessions/{id}/hide", confession.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(confession.getId()))
                .andExpect(jsonPath("$.hidden").value(true));
    }

    @Test
    void userCannotCallAdminModerationEndpoints() throws Exception {
        User normalUser = createUser("user-no-admin", Role.USER);
        User author = createUser("author-no-admin", Role.USER);
        Confession confession = createConfession(author, "forbidden target");

        mockMvc.perform(delete("/api/admin/confessions/{id}", confession.getId())
                        .header("Authorization", "Bearer " + tokenFor(normalUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        mockMvc.perform(patch("/api/admin/confessions/{id}/hide", confession.getId())
                        .header("Authorization", "Bearer " + tokenFor(normalUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void deleteNonExistingConfessionReturns404() throws Exception {
        User admin = createUser("admin-404", Role.ADMIN);
        long missingId = 999_999L;

        mockMvc.perform(delete("/api/admin/confessions/{id}", missingId)
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Confession not found with id " + missingId));
    }

    @Test
    void deleteWithRelatedEntitiesDoesNotFail() throws Exception {
        User admin = createUser("admin-related", Role.ADMIN);
        User author = createUser("author-related", Role.USER);
        User voterReporter = createUser("voter-related", Role.USER);

        Confession confession = createConfession(author, "with relations");
        createBoost(confession, author);
        createVote(confession, voterReporter);
        createReport(confession, voterReporter);

        mockMvc.perform(delete("/api/admin/confessions/{id}", confession.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNoContent());

        assertFalse(confessionRepository.existsById(confession.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(boostRepository.findByConfessionId(confession.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(confessionReportRepository.findAllByConfessionId(confession.getId()).isEmpty());
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
        confession.setLikeCount(0);
        confession.setDislikeCount(0);
        confession.setHidden(false);
        return confessionRepository.save(confession);
    }

    private void createBoost(Confession confession, User user) {
        Boost boost = new Boost();
        boost.setConfession(confession);
        boost.setUser(user);
        boost.setBoostType(BoostType.GOLD);
        boost.setDate(LocalDateTime.now());
        boostRepository.save(boost);
    }

    private void createVote(Confession confession, User voter) {
        ConfessionVote vote = ConfessionVote.builder()
                .confession(confession)
                .voteType(VoteType.LIKE)
                .voter(voter)
                .voterIp("127.0.0.1")
                .build();
        confessionVoteRepository.save(vote);
    }

    private void createReport(Confession confession, User reporter) {
        ConfessionReport report = ConfessionReport.builder()
                .confession(confession)
                .reporter(reporter)
                .reporterUser(reporter)
                .reporterIp("127.0.0.1")
                .reason("spam")
                .description("reported for moderation test")
                .severity("MEDIUM")
                .build();
        confessionReportRepository.save(report);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
