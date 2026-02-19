package com.confessionverse.backend;

import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubscriptionSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void adminCanCreateUpdateDeleteSubscription() throws Exception {
        User admin = createUser("sub-admin", Role.ADMIN);
        User target = createUser("sub-target", Role.USER);
        String adminToken = tokenFor(admin);

        String createBody = """
                {
                  "userId": %d,
                  "planType": "PREMIUM",
                  "startDate": "2026-02-01",
                  "endDate": "2026-03-01"
                }
                """.formatted(target.getId());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(target.getUsername()))
                .andExpect(jsonPath("$.planType").value("PREMIUM"));

        Subscription created = subscriptionRepository.findByUserId(target.getId()).stream()
                .findFirst()
                .orElseThrow();

        String updateBody = """
                {
                  "userId": %d,
                  "planType": "BASIC",
                  "startDate": "2026-02-01",
                  "endDate": "2026-04-01"
                }
                """.formatted(target.getId());

        mockMvc.perform(put("/api/subscriptions/{id}", created.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.planType").value("BASIC"));

        mockMvc.perform(delete("/api/subscriptions/{id}", created.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(subscriptionRepository.existsById(created.getId()));
    }

    @Test
    void userCannotCreateUpdateDeleteSubscription() throws Exception {
        User normalUser = createUser("sub-user", Role.USER);
        User target = createUser("sub-user-target", Role.USER);
        String userToken = tokenFor(normalUser);

        Subscription existing = createSubscription(target, "PREMIUM");

        String createBody = """
                {
                  "userId": %d,
                  "planType": "BASIC",
                  "startDate": "2026-02-01",
                  "endDate": "2026-03-01"
                }
                """.formatted(target.getId());

        mockMvc.perform(post("/api/subscriptions")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        String updateBody = """
                {
                  "userId": %d,
                  "planType": "PREMIUM",
                  "startDate": "2026-02-01",
                  "endDate": "2026-04-01"
                }
                """.formatted(target.getId());

        mockMvc.perform(put("/api/subscriptions/{id}", existing.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        mockMvc.perform(delete("/api/subscriptions/{id}", existing.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        assertTrue(subscriptionRepository.existsById(existing.getId()));
    }

    @Test
    void shouldKeepExistingAllowedEndpointsBehavior() throws Exception {
        User admin = createUser("sub-admin-view", Role.ADMIN);
        User owner = createUser("sub-owner-view", Role.USER);
        Subscription subscription = createSubscription(owner, "PREMIUM");

        String adminToken = tokenFor(admin);
        String ownerToken = tokenFor(owner);

        mockMvc.perform(get("/api/subscriptions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/subscriptions/{id}", subscription.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subscription.getId()))
                .andExpect(jsonPath("$.username").value(owner.getUsername()));
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

    private Subscription createSubscription(User owner, String planType) {
        Subscription subscription = new Subscription();
        subscription.setUser(owner);
        subscription.setPlanType(planType);
        subscription.setStartDate(LocalDate.of(2026, 2, 1));
        subscription.setEndDate(LocalDate.of(2026, 3, 1));
        return subscriptionRepository.save(subscription);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
