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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@ActiveProfiles("test")
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
                                  "usernameToAdd": "%s",
                                  "roomType": "DIRECT"
                                }
                                """.formatted(inviteeName)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long roomId = createJson.path("chatRoom").path("id").asLong();
        long inviteId = createJson.path("invite").path("id").asLong();

        mockMvc.perform(get("/api/chat-invites/me")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inviteId))
                .andExpect(jsonPath("$[0].inviterUsername").value(inviterName))
                .andExpect(jsonPath("$[0].inviteeUsername").value(inviteeName))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].inviterEmail").doesNotExist())
                .andExpect(jsonPath("$[0].inviteeEmail").doesNotExist());

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
    void communityMembershipIsRestoredAfterLeavingAndLoggingInAgain() throws Exception {
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
                .andExpect(jsonPath("$[*].id").value(hasItem((int) standardRoomId)));
    }

    @Test
    void declinedPrivateInviteDoesNotLeaveAnAccessibleConversation() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String inviterName = "decline-inviter-" + uid;
        String inviteeName = "decline-invitee-" + uid;
        String inviterToken = registerAndReturnToken(inviterName, inviterName + "@test.local");
        String inviteeToken = registerAndReturnToken(inviteeName, inviteeName + "@test.local");

        MvcResult createResult = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + inviterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameToAdd\":\"" + inviteeName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long roomId = createJson.path("chatRoom").path("id").asLong();
        long inviteId = createJson.path("invite").path("id").asLong();

        mockMvc.perform(post("/api/chat-invites/{inviteId}/decline", inviteId)
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chatrooms").header("Authorization", "Bearer " + inviterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) roomId))));
        mockMvc.perform(get("/api/chatrooms").header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem((int) roomId))));
    }

    @Test
    void duplicatePendingRequestReusesInvitationAndRoom() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "pending-a-" + uid;
        String secondName = "pending-b-" + uid;
        String firstToken = registerAndReturnToken(firstName, firstName + "@test.local");
        registerAndReturnToken(secondName, secondName + "@test.local");

        JsonNode first = requestPrivate(firstToken, secondName);
        JsonNode duplicate = requestPrivate(firstToken, secondName);

        assertEquals(first.path("chatRoomId").asLong(), duplicate.path("chatRoomId").asLong());
        assertEquals(first.path("invite").path("id").asLong(), duplicate.path("invite").path("id").asLong());
        assertEquals("PENDING", duplicate.path("status").asText());
    }

    @Test
    void crossedPendingRequestSurfacesOriginalInvitation() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "cross-a-" + uid;
        String secondName = "cross-b-" + uid;
        String firstToken = registerAndReturnToken(firstName, firstName + "@test.local");
        String secondToken = registerAndReturnToken(secondName, secondName + "@test.local");

        JsonNode original = requestPrivate(firstToken, secondName);
        JsonNode crossed = requestPrivate(secondToken, firstName);

        assertEquals(original.path("chatRoomId").asLong(), crossed.path("chatRoomId").asLong());
        assertEquals(original.path("invite").path("id").asLong(), crossed.path("invite").path("id").asLong());
        mockMvc.perform(post("/api/chat-invites/{id}/accept", crossed.path("invite").path("id").asLong())
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());
    }

    @Test
    void acceptedConversationIsReusedAndDeclinedRequestCanBeRetried() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "reuse-a-" + uid;
        String secondName = "reuse-b-" + uid;
        String firstToken = registerAndReturnToken(firstName, firstName + "@test.local");
        String secondToken = registerAndReturnToken(secondName, secondName + "@test.local");

        JsonNode initial = requestPrivate(firstToken, secondName);
        long initialRoomId = initial.path("chatRoomId").asLong();
        long initialInviteId = initial.path("invite").path("id").asLong();
        mockMvc.perform(post("/api/chat-invites/{id}/decline", initialInviteId)
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());

        JsonNode retried = requestPrivate(firstToken, secondName);
        org.junit.jupiter.api.Assertions.assertNotEquals(initialRoomId, retried.path("chatRoomId").asLong());
        mockMvc.perform(post("/api/chat-invites/{id}/accept", retried.path("invite").path("id").asLong())
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());

        JsonNode reused = requestPrivate(firstToken, secondName);
        assertEquals("ACCEPTED", reused.path("status").asText());
        assertEquals(retried.path("chatRoomId").asLong(), reused.path("chatRoomId").asLong());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCrossedRequestsProduceOnePendingRelationshipAndOneActiveRoom() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "concurrent-a-" + uid;
        String secondName = "concurrent-b-" + uid;
        String firstToken = registerAndReturnToken(firstName, firstName + "@test.local");
        String secondToken = registerAndReturnToken(secondName, secondName + "@test.local");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> firstRequest = executor.submit(() -> {
                start.await();
                return requestPrivate(firstToken, secondName);
            });
            Future<JsonNode> secondRequest = executor.submit(() -> {
                start.await();
                return requestPrivate(secondToken, firstName);
            });
            start.countDown();
            JsonNode first = firstRequest.get();
            JsonNode second = secondRequest.get();

            assertEquals(first.path("chatRoomId").asLong(), second.path("chatRoomId").asLong());
            assertEquals(first.path("invite").path("id").asLong(), second.path("invite").path("id").asLong());

            String inviteeName = first.path("invite").path("inviteeUsername").asText();
            String acceptToken = inviteeName.equals(firstName) ? firstToken : secondToken;
            mockMvc.perform(post("/api/chat-invites/{id}/accept", first.path("invite").path("id").asLong())
                            .header("Authorization", "Bearer " + acceptToken))
                    .andExpect(status().isOk());

            User firstUser = userRepository.findByUsername(firstName).orElseThrow();
            User secondUser = userRepository.findByUsername(secondName).orElseThrow();
            Integer activeRooms = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT cu.chatroom_id
                        FROM chatroom_users cu
                        JOIN chat_room cr ON cr.id = cu.chatroom_id
                        WHERE cr.room_type = 'DIRECT' AND cu.active = TRUE
                          AND cu.user_id IN (?, ?)
                        GROUP BY cu.chatroom_id
                        HAVING COUNT(DISTINCT cu.user_id) = 2
                    ) pair_rooms
                    """, Integer.class, firstUser.getId(), secondUser.getId());
            assertEquals(1, activeRooms);
        } finally {
            executor.shutdownNow();
        }
    }

    private JsonNode requestPrivate(String token, String targetUsername) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameToAdd\":\"" + targetUsername + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
