package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatRoomGetAllIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void getAllShouldBeNullSafeAndReturnOnlyExpectedRoomsWithoutPasswordLeak() throws Exception {
        User viewer = createUser("rooms-viewer");
        User otherA = createUser("rooms-other-a");
        User otherB = createUser("rooms-other-b");

        LinkedHashSet<User> creatorParticipants = new LinkedHashSet<>();
        creatorParticipants.add(viewer);
        ChatRoom creatorVisible = createRoom("creator-visible", viewer, creatorParticipants, new HashSet<>());

        LinkedHashSet<User> participantSet = new LinkedHashSet<>();
        participantSet.add(viewer);
        participantSet.add(otherA);
        ChatRoom participantVisible = createRoom("participant-visible", otherA, participantSet, new HashSet<>());

        LinkedHashSet<User> notMineParticipants = new LinkedHashSet<>();
        notMineParticipants.add(otherA);
        notMineParticipants.add(otherB);
        ChatRoom notVisible = createRoom("not-visible", otherA, notMineParticipants, new HashSet<>());

        HashSet<User> hiddenByViewer = new HashSet<>();
        hiddenByViewer.add(viewer);
        ChatRoom hiddenRoom = createRoom("hidden-room", viewer, new LinkedHashSet<>(), hiddenByViewer);

        // Legacy/bad row simulation: null creator
        ChatRoom nullCreatorRoom = createRoom("legacy-null-creator", null, new LinkedHashSet<>(), new HashSet<>());

        String token = jwtUtil.generateToken(viewer.getEmail(), viewer.getEmail(), viewer.getRole().name());

        mockMvc.perform(get("/api/chatrooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[*].id").value(hasItems(
                        creatorVisible.getId().intValue(),
                        participantVisible.getId().intValue()
                )))
                .andExpect(jsonPath("$[*].id").value(not(hasItems(
                        notVisible.getId().intValue(),
                        hiddenRoom.getId().intValue(),
                        nullCreatorRoom.getId().intValue()
                ))));
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

    private ChatRoom createRoom(String name, User creator, LinkedHashSet<User> participants, HashSet<User> hiddenBy) {
        ChatRoom room = new ChatRoom();
        room.setUsername(name);
        room.setCreator(creator);
        room.setParticipants(participants);
        room.setHiddenBy(hiddenBy);
        return chatRoomRepository.save(room);
    }
}
