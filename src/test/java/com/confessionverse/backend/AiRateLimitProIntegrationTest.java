package com.confessionverse.backend;

import com.confessionverse.backend.dto.responseDTO.AiResponseDto;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.confessionverse.backend.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiRateLimitProIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private AiService aiService;

    @Test
    void freeUserShouldHitAiLimit() throws Exception {
        when(aiService.getAiReply(any())).thenReturn(new AiResponseDto("ok"));
        User freeUser = createUser("free-ai-limit");
        String token = tokenFor(freeUser);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/ai/reply")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "message": "help me"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reply").value("ok"));
        }

        mockMvc.perform(post("/api/ai/reply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "help me"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("FREE_LIMIT_REACHED"));
    }

    @Test
    void proUserShouldHaveUnlimitedAiByRateLimit() throws Exception {
        when(aiService.getAiReply(any())).thenReturn(new AiResponseDto("ok"));
        User proUser = createUser("pro-ai-unlimited");
        createActiveSubscription(proUser);
        String token = tokenFor(proUser);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/ai/reply")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "message": "help me"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reply").value("ok"));
        }
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
