package com.confessionverse.backend;

import com.confessionverse.backend.model.*;
import com.confessionverse.backend.repository.*;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.admin.protected-email=protected-admin@test.local")
@AutoConfigureMockMvc
@Transactional
class AdminUserDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private ConfessionVoteRepository confessionVoteRepository;

    @Autowired
    private ConfessionReportRepository confessionReportRepository;

    @Autowired
    private BoostRepository boostRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void adminCanDeleteNormalUser() throws Exception {
        User admin = createUser("admin-delete", "admin-delete@test.local", Role.ADMIN);
        User target = createUser("target-delete", "target-delete@test.local", Role.USER);

        mockMvc.perform(delete("/api/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(target.getId()));
    }

    @Test
    void adminCannotDeleteProtectedAdmin() throws Exception {
        User callerAdmin = createUser("caller-admin", "caller-admin@test.local", Role.ADMIN);
        User protectedAdmin = createUser("protected", "protected-admin@test.local", Role.ADMIN);

        mockMvc.perform(delete("/api/admin/users/{id}", protectedAdmin.getId())
                        .header("Authorization", "Bearer " + tokenFor(callerAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Protected admin account cannot be deleted"));
    }

    @Test
    void userCannotCallAdminDeleteEndpoint() throws Exception {
        User normalUser = createUser("normal-user", "normal-user@test.local", Role.USER);
        User target = createUser("target", "target@test.local", Role.USER);

        mockMvc.perform(delete("/api/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + tokenFor(normalUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));
    }

    @Test
    void deletingMissingUserReturns404() throws Exception {
        User admin = createUser("admin-missing", "admin-missing@test.local", Role.ADMIN);
        long missingId = 999_999L;

        mockMvc.perform(delete("/api/admin/users/{id}", missingId)
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id " + missingId));
    }

    @Test
    void deletingUserWithRelatedDataDoesNotFail() throws Exception {
        User admin = createUser("admin-related", "admin-related@test.local", Role.ADMIN);
        User target = createUser("target-related", "target-related@test.local", Role.USER);
        User other = createUser("other-related", "other-related@test.local", Role.USER);

        Confession confession = createConfession(target, "target confession");
        createBoost(confession, target);
        createVote(confession, target, "127.0.0.1");
        createReport(confession, target);
        createSubscription(target);
        createChatArtifacts(target, other);

        mockMvc.perform(delete("/api/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(target.getId()));
        assertTrue(confessionRepository.findByUserId(target.getId()).isEmpty());
        assertTrue(subscriptionRepository.findByUserId(target.getId()).isEmpty());
        assertTrue(boostRepository.findByUserId(target.getId()).isEmpty());
    }

    @Test
    void fallbackDeleteUsersEndpointShouldRemainStableForAdmin() throws Exception {
        User admin = createUser("admin-fallback", "admin-fallback@test.local", Role.ADMIN);
        User target = createUser("target-fallback", "target-fallback@test.local", Role.USER);

        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk());

        assertFalse(userRepository.existsById(target.getId()));
    }

    private User createUser(String usernamePrefix, String email, Role role) {
        User user = new User();
        user.setUsername(usernamePrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(email);
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

    private void createVote(Confession confession, User voter, String voterIp) {
        ConfessionVote vote = ConfessionVote.builder()
                .confession(confession)
                .voter(voter)
                .voterIp(voterIp)
                .voteType(VoteType.LIKE)
                .build();
        confessionVoteRepository.save(vote);
    }

    private void createReport(Confession confession, User reporter) {
        ConfessionReport report = ConfessionReport.builder()
                .confession(confession)
                .reporterUser(reporter)
                .reporter(reporter)
                .reporterIp("127.0.0.1")
                .reason("spam")
                .description("report for user deletion test")
                .severity("MEDIUM")
                .build();
        confessionReportRepository.save(report);
    }

    private void createSubscription(User user) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlanType("PREMIUM");
        subscription.setStartDate(LocalDate.of(2026, 1, 1));
        subscription.setEndDate(LocalDate.of(2026, 2, 1));
        subscriptionRepository.save(subscription);
    }

    private void createChatArtifacts(User target, User other) {
        ChatRoom room = new ChatRoom();
        room.setCreator(target);
        room.setUsername("room-" + UUID.randomUUID());
        LinkedHashSet<User> participants = new LinkedHashSet<>();
        participants.add(target);
        participants.add(other);
        room.setParticipants(participants);
        room.setHiddenBy(new HashSet<>());
        ChatRoom savedRoom = chatRoomRepository.save(room);

        Message message = new Message();
        message.setChatRoom(savedRoom);
        message.setSender(target);
        message.setContent("message from target");
        messageRepository.save(message);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
