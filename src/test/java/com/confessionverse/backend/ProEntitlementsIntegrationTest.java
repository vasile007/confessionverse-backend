package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ConfessionRepository;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProEntitlementsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void freeUserShouldBeBlockedFromPremiumRoom() throws Exception {
        User freeUser = createUser("free-premium-room");
        String token = tokenFor(freeUser);

        mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Premium room",
                                  "roomType": "%s"
                                }
                                """.formatted(ChatRoomType.HEARTBEAT.name())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PREMIUM_ROOM_REQUIRED"));
    }

    @Test
    void proUserShouldBeAllowedInPremiumRoom() throws Exception {
        User proUser = createUser("pro-premium-room");
        createActiveSubscription(proUser);
        String token = tokenFor(proUser);

        mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Premium room",
                                  "roomType": "%s"
                                }
                                """.formatted(ChatRoomType.HEARTBEAT.name())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomType").value(ChatRoomType.HEARTBEAT.name()))
                .andExpect(jsonPath("$.creator.planType").value("PRO"))
                .andExpect(jsonPath("$.creator.premium").value(true));
    }

    @Test
    void usersMeShouldExposePremiumAndPlanTypeFromSubscriptionStatus() throws Exception {
        User proUser = createUser("pro-users-me");
        createActiveSubscription(proUser);
        String token = tokenFor(proUser);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.planType").value("PRO"));
    }

    @Test
    void publicConfessionsShouldExposePremiumHighlightAndAuthorPlanForPro() throws Exception {
        User proAuthor = createUser("pro-confession-author");
        createActiveSubscription(proAuthor);

        Confession confession = new Confession();
        confession.setUser(proAuthor);
        confession.setContent("pro confession");
        confession = confessionRepository.save(confession);

        mockMvc.perform(get("/api/confessions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==%d)].premiumHighlight".formatted(confession.getId())).value(true))
                .andExpect(jsonPath("$[?(@.id==%d)].highlighted".formatted(confession.getId())).value(true))
                .andExpect(jsonPath("$[?(@.id==%d)].isPremium".formatted(confession.getId())).value(true))
                .andExpect(jsonPath("$[?(@.id==%d)].user.planType".formatted(confession.getId())).value("PRO"))
                .andExpect(jsonPath("$[?(@.id==%d)].owner.premium".formatted(confession.getId())).value(true));
    }

    private User createUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + uid);
        user.setEmail(prefix + "-" + uid + "@test.local");
        user.setPasswordHash("test-hash");
        user.setRole(Role.USER);
        user.setPremium(false);
        return userRepository.save(user);
    }

    private void createActiveSubscription(User user) {
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setSubscriber(user);
        sub.setPlanType("PRO");
        sub.setStatus("active");
        subscriptionRepository.save(sub);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
