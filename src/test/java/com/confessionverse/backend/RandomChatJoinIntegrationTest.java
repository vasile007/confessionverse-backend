package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.confessionverse.backend.service.RandomChatMatchmakingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RandomChatJoinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RandomChatMatchmakingService matchmakingService;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void clearWaitingUsers() {
        createdUserIds.forEach(matchmakingService::cancel);
    }

    @MockBean
    private Clock clock;

    @Test
    void randomJoinShouldReturnStandardForFreeUserDaytime() throws Exception {
        stubClock("2026-02-15T14:00:00Z");
        User freeUser = createUser("random-free-day");

        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.roomType").value(ChatRoomType.STANDARD.name()));
    }

    @Test
    void secondUserShouldMatchFirstWaitingUserWithoutSelfMatching() throws Exception {
        stubClock("2026-02-15T14:00:00Z");
        User first = createUser("random-first");
        User second = createUser("random-second");

        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + tokenFor(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + tokenFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.chatRoom.participants.length()").value(2))
                .andExpect(jsonPath("$.chatRoom.participants[*].id").value(hasItems(
                        first.getId().intValue(), second.getId().intValue())))
                .andExpect(jsonPath("$.chatRoom.id").isNumber());
    }

    @Test
    void randomJoinCanReturnLateNightOnlyInsideNightWindow() throws Exception {
        User freeUser = createUser("random-free-night");

        stubClock("2026-02-15T14:00:00Z");
        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"LATE_NIGHT\"}")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isForbidden());

        stubClock("2026-02-15T23:00:00Z");
        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"LATE_NIGHT\"}")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.roomType").value(ChatRoomType.LATE_NIGHT.name()));
    }

    @Test
    void randomJoinNeverReturnsHeartbeatToFreeUser() throws Exception {
        stubClock("2026-02-15T23:00:00Z");
        User freeUser = createUser("random-free-no-heartbeat");

        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"HEARTBEAT\"}")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void randomJoinMayReturnHeartbeatForProUser() throws Exception {
        stubClock("2026-02-15T14:00:00Z");
        User proUser = createUser("random-pro-heartbeat");
        createActiveSubscription(proUser);

        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"HEARTBEAT\"}")
                        .header("Authorization", "Bearer " + tokenFor(proUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.roomType").value(ChatRoomType.HEARTBEAT.name()));
    }

    @Test
    void randomJoinCreatesAndReturnsStandardIfNoRoomsExist() throws Exception {
        stubClock("2026-02-15T14:00:00Z");
        jdbcTemplate.execute("DELETE FROM message");
        jdbcTemplate.execute("DELETE FROM chat_invitations");
        jdbcTemplate.execute("DELETE FROM chatroom_hidden_by");
        jdbcTemplate.execute("DELETE FROM chatroom_users");
        jdbcTemplate.execute("DELETE FROM chat_room");

        User freeUser = createUser("random-create-standard");
        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomType\":\"STANDARD\"}")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        long standardRooms = chatRoomRepository.findAllByRoomTypeOrderByIdAsc(ChatRoomType.STANDARD).size();
        org.junit.jupiter.api.Assertions.assertTrue(standardRooms >= 1);
    }

    @Test
    void getChatroomsShouldReturnAtLeastOneAccessibleRoomForNormalUser() throws Exception {
        stubClock("2026-02-15T14:00:00Z");
        User freeUser = createUser("list-normal-user");

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenFor(freeUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[*].roomType").value(hasItem(ChatRoomType.STANDARD.name())));
    }

    private void stubClock(String instantIso) {
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(Instant.parse(instantIso));
    }

    private User createUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + uid);
        user.setEmail(prefix + "-" + uid + "@test.local");
        user.setPasswordHash("test-hash");
        user.setRole(Role.USER);
        user.setPremium(false);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
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
