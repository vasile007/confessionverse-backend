package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StandardRoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void standardRoomShouldExistAndStaySingle() {
        List<ChatRoom> standardRooms = chatRoomRepository.findAllByRoomTypeOrderByIdAsc(ChatRoomType.STANDARD);
        assertFalse(standardRooms.isEmpty());
        assertEquals(1, standardRooms.size());
        assertNotNull(standardRooms.get(0).getId());
    }

    @Test
    void registerShouldAutoAddUserToStandardRoom() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String username = "std-reg-" + uid;
        String email = "std-reg-" + uid + "@test.local";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "pass123"
                                }
                                """.formatted(username, email)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        ChatRoom standardRoom = standardRoom();
        assertEquals(1, membershipCount(standardRoom.getId(), user.getId()));
    }

    @Test
    void loginAndMeShouldNotAutoCreateMembershipForLegacyUser() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "std-login-" + uid + "@test.local";
        User user = new User();
        user.setUsername("std-login-" + uid);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("pass123"));
        user.setRole(Role.USER);
        user.setPremium(false);
        user = userRepository.save(user);
        final Long userId = user.getId();

        ChatRoom standardRoom = standardRoom();
        standardRoom.getParticipants().removeIf(u -> u != null && userId.equals(u.getId()));
        chatRoomRepository.save(standardRoom);
        assertEquals(0, membershipCount(standardRoom.getId(), userId));

        String token = loginAndReturnToken(email, "pass123");
        assertEquals(0, membershipCount(standardRoom.getId(), userId));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        assertEquals(0, membershipCount(standardRoom.getId(), userId));

        loginAndReturnToken(email, "pass123");
        assertEquals(0, membershipCount(standardRoom.getId(), userId));
    }

    @Test
    void chatroomsShouldIncludeStandardForAuthenticatedUser() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "std-list-" + uid + "@test.local";
        String token = registerAndReturnToken("std-list-" + uid, email);

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].roomType").value(hasItem("STANDARD")));
    }

    @Test
    void shouldSendMessageInStandardRoom() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String token = registerAndReturnToken("std-msg-" + uid, "std-msg-" + uid + "@test.local");

        MvcResult listResult = mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rooms = objectMapper.readTree(listResult.getResponse().getContentAsString());
        Long standardRoomId = null;
        for (JsonNode room : rooms) {
            if ("STANDARD".equals(room.path("roomType").asText())) {
                standardRoomId = room.path("id").asLong();
                break;
            }
        }
        assertNotNull(standardRoomId);

        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatRoomId": %d,
                                  "content": "hello-standard"
                                }
                                """.formatted(standardRoomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatRoomId").value(standardRoomId));
    }

    @Test
    void participantCanLeaveStandardRoomAndRoomStaysInDb() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String email = "std-leave-" + uid + "@test.local";
        String token = registerAndReturnToken("std-leave-" + uid, email);
        User user = userRepository.findByEmail(email).orElseThrow();

        Long standardRoomId = standardRoom().getId();
        assertEquals(1, membershipCount(standardRoomId, user.getId()));

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", standardRoomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertEquals(1, membershipCount(standardRoomId, user.getId()));
        assertEquals(0, activeMembershipCount(standardRoomId, user.getId()));
        assertTrue(chatRoomRepository.findById(standardRoomId).isPresent());
    }

    @Test
    void acceptInviteAfterLeaveShouldReactivateWithoutDuplicateMembership() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String userAName = "inv-a-" + uid;
        String userBName = "inv-b-" + uid;
        String tokenA = registerAndReturnToken(userAName, userAName + "@test.local");
        String tokenB = registerAndReturnToken(userBName, userBName + "@test.local");
        User userB = userRepository.findByUsername(userBName).orElseThrow();

        MvcResult createResult = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameToAdd": "%s"
                                }
                                """.formatted(userBName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long roomId = createJson.path("chatRoom").path("id").asLong();
        long firstInviteId = createJson.path("invite").path("id").asLong();

        mockMvc.perform(post("/api/chat-invites/{inviteId}/accept", firstInviteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", roomId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        MvcResult reInviteResult = mockMvc.perform(post("/api/chatrooms/{id}/invites", roomId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameToAdd": "%s"
                                }
                                """.formatted(userBName)))
                .andExpect(status().isOk())
                .andReturn();
        long secondInviteId = objectMapper.readTree(reInviteResult.getResponse().getContentAsString())
                .path("invitation")
                .path("id")
                .asLong();

        mockMvc.perform(post("/api/chat-invites/{inviteId}/accept", secondInviteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) roomId)));

        assertEquals(1, membershipCount(roomId, userB.getId()));
    }

    @Test
    void userWithInactiveMembershipCannotSendMessages() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String userAName = "msg-a-" + uid;
        String userBName = "msg-b-" + uid;
        String tokenA = registerAndReturnToken(userAName, userAName + "@test.local");
        String tokenB = registerAndReturnToken(userBName, userBName + "@test.local");

        MvcResult createResult = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameToAdd": "%s"
                                }
                                """.formatted(userBName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long roomId = createJson.path("chatRoom").path("id").asLong();
        long inviteId = createJson.path("invite").path("id").asLong();

        mockMvc.perform(post("/api/chat-invites/{inviteId}/accept", inviteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", roomId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatRoomId": %d,
                                  "content": "blocked-after-leave"
                                }
                                """.formatted(roomId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only active participants can send messages in this chat"));
    }

    private String registerAndReturnToken(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "pass123"
                                }
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String loginAndReturnToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private ChatRoom standardRoom() {
        return chatRoomRepository.findAllByRoomTypeOrderByIdAsc(ChatRoomType.STANDARD).stream()
                .findFirst()
                .orElseThrow();
    }

    private int membershipCount(Long chatRoomId, Long userId) {
        chatRoomRepository.flush();
        userRepository.flush();
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chatroom_users WHERE chatroom_id = ? AND user_id = ?",
                Integer.class,
                chatRoomId,
                userId
        );
        return result == null ? 0 : result;
    }

    private int activeMembershipCount(Long chatRoomId, Long userId) {
        chatRoomRepository.flush();
        userRepository.flush();
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chatroom_users WHERE chatroom_id = ? AND user_id = ? AND active = TRUE",
                Integer.class,
                chatRoomId,
                userId
        );
        return result == null ? 0 : result;
    }
}
