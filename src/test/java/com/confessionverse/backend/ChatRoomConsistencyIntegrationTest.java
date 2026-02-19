package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatRoomConsistencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createWithInviteTwiceShouldCreateDistinctRooms() throws Exception {
        User creator = createUser("pair-creator", Role.USER);
        User target = createUser("pair-target", Role.USER);
        String token = tokenFor(creator);

        String body = """
                {
                  "usernameToAdd": "%s"
                }
                """.formatted(target.getUsername());

        MvcResult first = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        long firstId = responseId(first);

        MvcResult second = mockMvc.perform(post("/api/chatrooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        long secondId = responseId(second);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstId, secondId);
    }

    @Test
    void userListShouldContainOnlyAccessibleRooms() throws Exception {
        User viewer = createUser("list-viewer", Role.USER);
        User otherA = createUser("list-other-a", Role.USER);
        User otherB = createUser("list-other-b", Role.USER);

        ChatRoom visible = createRoom("visible", viewer, setOf(viewer, otherA), new HashSet<>());
        ChatRoom hidden = createRoom("hidden", viewer, setOf(viewer, otherA), setOf(viewer));
        ChatRoom unrelated = createRoom("unrelated", otherA, setOf(otherA, otherB), new HashSet<>());

        mockMvc.perform(delete("/api/chatrooms/{id}/my-chat", hidden.getId())
                        .header("Authorization", "Bearer " + tokenFor(viewer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenFor(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[*].id").value(hasItem(visible.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(hidden.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(unrelated.getId().intValue()))));
    }

    @Test
    void leaveShouldWorkForVisibleRoomAndBeIdempotent() throws Exception {
        User viewer = createUser("leave-viewer", Role.USER);
        User other = createUser("leave-other", Role.USER);
        ChatRoom room = createRoom("leave-room", other, setOf(viewer, other), new HashSet<>());
        String token = tokenFor(viewer);

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(room.getId().intValue())));

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", room.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only participants can leave this chat"));

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(room.getId().intValue()))));

        org.junit.jupiter.api.Assertions.assertTrue(chatRoomRepository.findById(room.getId()).isPresent());
    }

    @Test
    void userCannotLeaveUnrelatedRoom() throws Exception {
        User roomOwner = createUser("owner", Role.USER);
        User roomMember = createUser("member", Role.USER);
        User stranger = createUser("stranger", Role.USER);

        ChatRoom room = createRoom("unrelated-room", roomOwner, setOf(roomOwner, roomMember), new HashSet<>());

        mockMvc.perform(delete("/api/chatrooms/{id}/leave", room.getId())
                        .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only participants can leave this chat"));
    }

    @Test
    void leaveShouldReturnBadRequestForInvalidRoomId() throws Exception {
        User user = createUser("invalid-roomid", Role.USER);
        mockMvc.perform(delete("/api/chatrooms/{id}/leave", "invalid")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myChatDeleteShouldHideOnlyForCurrentUser() throws Exception {
        User userA = createUser("hide-a", Role.USER);
        User userB = createUser("hide-b", Role.USER);
        ChatRoom room = createRoom("hide-room", userA, setOf(userA, userB), new HashSet<>());

        mockMvc.perform(delete("/api/chatrooms/{id}/my-chat", room.getId())
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenFor(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(not(hasItem(room.getId().intValue()))));

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + tokenFor(userB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(room.getId().intValue())));
    }

    @Test
    void adminScopeShouldDefaultToOwnVisibleAndAllowAllWhenRequested() throws Exception {
        User admin = createUser("scope-admin", Role.ADMIN);
        User userA = createUser("scope-a", Role.USER);
        User userB = createUser("scope-b", Role.USER);

        ChatRoom ownVisible = createRoom("admin-visible", admin, setOf(admin, userA), new HashSet<>());
        ChatRoom foreignRoom = createRoom("foreign", userA, setOf(userA, userB), new HashSet<>());

        String adminToken = tokenFor(admin);

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[*].id").value(hasItem(ownVisible.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(foreignRoom.getId().intValue()))));

        mockMvc.perform(get("/api/chatrooms?scope=all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(ownVisible.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(hasItem(foreignRoom.getId().intValue())));
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

    private ChatRoom createRoom(String name, User creator, LinkedHashSet<User> participants, HashSet<User> hiddenBy) {
        ChatRoom room = new ChatRoom();
        room.setUsername(name);
        room.setCreator(creator);
        room.setParticipants(participants);
        room.setHiddenBy(hiddenBy);
        return chatRoomRepository.save(room);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }

    private long responseId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode idNode = node.get("id");
        if (idNode == null || idNode.isNull()) {
            idNode = node.path("chatRoom").path("id");
        }
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            throw new IllegalStateException("Could not resolve chat room id from response: " + node);
        }
        return idNode.asLong();
    }

    private LinkedHashSet<User> setOf(User... users) {
        LinkedHashSet<User> result = new LinkedHashSet<>();
        for (User user : users) {
            result.add(user);
        }
        return result;
    }
}
