package com.confessionverse.backend;

import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InviteMembershipFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void inviteAcceptShouldAllowBothSidesAndBlockNonParticipant() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String inviterName = "inviter-" + uid;
        String inviteeName = "invitee-" + uid;
        String strangerName = "stranger-" + uid;

        String inviterToken = registerAndReturnToken(inviterName, inviterName + "@test.local");
        String inviteeToken = registerAndReturnToken(inviteeName, inviteeName + "@test.local");
        String strangerToken = registerAndReturnToken(strangerName, strangerName + "@test.local");

        User inviter = userRepository.findByUsername(inviterName).orElseThrow();
        User invitee = userRepository.findByUsername(inviteeName).orElseThrow();

        MvcResult createResult = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + inviterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameToAdd": "%s"
                                }
                                """.formatted(inviteeName)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long roomId = createJson.path("chatRoom").path("id").asLong();
        long inviteId = createJson.path("invite").path("id").asLong();

        mockMvc.perform(post("/api/chat-invites/{inviteId}/accept", inviteId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatRoom.id").value(roomId))
                .andExpect(jsonPath("$.chatRoom.roomType").value("DIRECT"))
                .andExpect(jsonPath("$.chatRoom.participants[*].id").value(hasItem(inviter.getId().intValue())))
                .andExpect(jsonPath("$.chatRoom.participants[*].id").value(hasItem(invitee.getId().intValue())));

        assertEquals(1, activeMembershipCount(roomId, inviter.getId()));
        assertEquals(1, activeMembershipCount(roomId, invitee.getId()));

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + inviterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) roomId)));

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem((int) roomId)));

        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + inviterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatRoomId": %d,
                                  "content": "hello-from-inviter"
                                }
                                """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatRoomId").value(roomId));

        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + inviteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatRoomId": %d,
                                  "content": "hello-from-invitee"
                                }
                                """.formatted(roomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatRoomId").value(roomId));

        mockMvc.perform(post("/api/messages")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatRoomId": %d,
                                  "content": "should-fail"
                                }
                                """.formatted(roomId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_PARTICIPANT"));
    }

    @Test
    void leavingRoomShouldNotAutoRejoinAfterLogoutLogin() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String username = "leave-rejoin-" + uid;
        String email = username + "@test.local";
        String token = registerAndReturnToken(username, email);

        MvcResult rooms = mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roomList = objectMapper.readTree(rooms.getResponse().getContentAsString());
        long standardRoomId = -1L;
        for (JsonNode room : roomList) {
            if ("STANDARD".equals(room.path("roomType").asText())) {
                standardRoomId = room.path("id").asLong();
                break;
            }
        }

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", standardRoomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        String loginToken = loginAndReturnToken(email, "pass123");
        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) standardRoomId))));
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
        return json.path("token").asText();
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
        return json.path("token").asText();
    }

    private int activeMembershipCount(Long chatRoomId, Long userId) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chatroom_users WHERE chatroom_id = ? AND user_id = ? AND active = TRUE",
                Integer.class,
                chatRoomId,
                userId
        );
        return result == null ? 0 : result;
    }
}
