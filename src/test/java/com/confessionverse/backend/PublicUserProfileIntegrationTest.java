package com.confessionverse.backend;

import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ConfessionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicUserProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void publicProfileShouldReturnSafeFieldsAndStatsForExistingUsername() throws Exception {
        User targetUser = createUser("profile-target");
        createConfession(targetUser, "older confession", 3, 1, LocalDateTime.now().minusHours(2));
        createConfession(targetUser, "newer confession", 5, 2, LocalDateTime.now().minusHours(1));

        String token = createToken(createUser("profile-viewer"));

        mockMvc.perform(get("/api/users/public/{username}", targetUser.getUsername())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUser.getId()))
                .andExpect(jsonPath("$.username").value(targetUser.getUsername()))
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.planType").value("FREE"))
                .andExpect(jsonPath("$.stats.totalLikes").value(8))
                .andExpect(jsonPath("$.stats.totalDislikes").value(3));
    }

    @Test
    void publicProfileShouldReturn404ForMissingUsername() throws Exception {
        String token = createToken(createUser("missing-user-viewer"));

        mockMvc.perform(get("/api/users/public/{username}", "does-not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with username: does-not-exist"));
    }

    @Test
    void publicProfileShouldExcludePrivateSensitiveFields() throws Exception {
        User targetUser = createUser("safe-fields-target");
        String token = createToken(createUser("safe-fields-viewer"));

        mockMvc.perform(get("/api/users/public/{username}", targetUser.getUsername())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void byUserEndpointShouldReturnOnlyThatUsersConfessionsNewestFirst() throws Exception {
        User targetUser = createUser("conf-target");
        User otherUser = createUser("conf-other");

        Confession older = createConfession(targetUser, "target older", 1, 0, LocalDateTime.now().minusHours(3));
        Confession newer = createConfession(targetUser, "target newer", 2, 0, LocalDateTime.now().minusHours(1));
        createConfession(otherUser, "other confession", 9, 9, LocalDateTime.now().minusHours(2));

        String token = createToken(createUser("conf-viewer"));

        mockMvc.perform(get("/api/confessions/public/by-user/{username}", targetUser.getUsername())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()))
                .andExpect(jsonPath("$[0].username").value(targetUser.getUsername()))
                .andExpect(jsonPath("$[1].username").value(targetUser.getUsername()))
                .andExpect(jsonPath("$[0].author").value("Anonim"))
                .andExpect(jsonPath("$[1].author").value("Anonim"))
                .andExpect(jsonPath("$[0].premiumHighlight").value(false))
                .andExpect(jsonPath("$[0].user.planType").value("FREE"))
                .andExpect(jsonPath("$[1].owner.premium").value(false));
    }

    private User createUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + uid);
        user.setEmail(prefix + "-" + uid + "@test.local");
        user.setPasswordHash("test-hash");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private Confession createConfession(User user, String content, int likes, int dislikes, LocalDateTime createdAt) {
        Confession confession = new Confession();
        confession.setUser(user);
        confession.setContent(content);
        confession.setLikeCount(likes);
        confession.setDislikeCount(dislikes);
        Confession saved = confessionRepository.save(confession);
        saved.setCreatedAt(createdAt);
        return confessionRepository.save(saved);
    }

    private String createToken(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
