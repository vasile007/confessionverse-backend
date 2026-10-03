package com.confessionverse.backend;

import com.confessionverse.backend.dto.responseDTO.RandomChatMatchDTO;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.confessionverse.backend.service.RandomChatMatchmakingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RandomChatJoinIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomMembershipRepository membershipRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RandomChatMatchmakingService matchmakingService;

    @Test
    void firstAndSecondUsersJoinTheSamePersistentRandomRoom() {
        User first = createUser("random-first");
        User second = createUser("random-second");

        RandomChatMatchDTO firstJoin = matchmakingService.join(first);
        RandomChatMatchDTO secondJoin = matchmakingService.join(second);

        assertEquals("JOINED", firstJoin.getStatus());
        assertEquals(firstJoin.getChatRoom().getId(), secondJoin.getChatRoom().getId());
        assertEquals(2, membershipRepository.countByChatRoom_IdAndActiveTrue(firstJoin.getChatRoom().getId()));
    }

    @Test
    void roomAcceptsSixAndSeventhIsAssignedElsewhere() {
        List<RandomChatMatchDTO> joins = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            joins.add(matchmakingService.join(createUser("capacity-" + index)));
        }

        Long firstRoomId = joins.get(0).getChatRoom().getId();
        for (int index = 1; index < 6; index++) {
            assertEquals(firstRoomId, joins.get(index).getChatRoom().getId());
        }
        assertEquals(6, membershipRepository.countByChatRoom_IdAndActiveTrue(firstRoomId));
        assertNotEquals(firstRoomId, joins.get(6).getChatRoom().getId());
    }

    @Test
    void duplicateJoinIsIdempotent() {
        User user = createUser("duplicate");
        RandomChatMatchDTO first = matchmakingService.join(user);
        RandomChatMatchDTO repeated = matchmakingService.join(user);

        assertEquals(first.getChatRoom().getId(), repeated.getChatRoom().getId());
        assertEquals(1, membershipRepository.countByChatRoom_IdAndActiveTrue(first.getChatRoom().getId()));
    }

    @Test
    void leaveDeactivatesOnlyTheCallingMember() throws Exception {
        User first = createUser("leave-first");
        User second = createUser("leave-second");
        Long roomId = matchmakingService.join(first).getChatRoom().getId();
        matchmakingService.join(second);

        mockMvc.perform(delete("/api/chatrooms/{roomId}/leave", roomId)
                        .header("Authorization", "Bearer " + tokenFor(first)))
                .andExpect(status().isNoContent());

        assertTrue(membershipRepository.findByChatRoom_IdAndUser_IdAndActiveTrue(roomId, first.getId()).isEmpty());
        assertTrue(membershipRepository.findByChatRoom_IdAndUser_IdAndActiveTrue(roomId, second.getId()).isPresent());
    }

    @Test
    void nextLeavesOldRoomAndAvoidsImmediateReassignment() throws Exception {
        User user = createUser("next-user");
        Long oldRoomId = matchmakingService.join(user).getChatRoom().getId();

        mockMvc.perform(post("/api/chatrooms/random-next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentRoomId\":" + oldRoomId + "}")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.chatRoom.id").value(org.hamcrest.Matchers.not(oldRoomId.intValue())));

        assertTrue(membershipRepository.findByChatRoom_IdAndUser_IdAndActiveTrue(oldRoomId, user.getId()).isEmpty());
    }

    @Test
    void repeatedNextWithTheSameOldRoomIsIdempotent() {
        User user = createUser("next-repeat");
        Long oldRoomId = matchmakingService.join(user).getChatRoom().getId();

        RandomChatMatchDTO firstNext = matchmakingService.next(user, oldRoomId);
        RandomChatMatchDTO repeatedNext = matchmakingService.next(user, oldRoomId);

        assertEquals(firstNext.getChatRoom().getId(), repeatedNext.getChatRoom().getId());
        assertEquals(1, membershipRepository.countByChatRoom_IdAndActiveTrue(firstNext.getChatRoom().getId()));
    }

    @Test
    void heartbeatRefreshesPersistentRandomMembershipLease() {
        User user = createUser("heartbeat");
        Long roomId = matchmakingService.join(user).getChatRoom().getId();

        matchmakingService.heartbeat(user.getId(), roomId);

        assertTrue(membershipRepository.findByChatRoom_IdAndUser_IdAndActiveTrue(roomId, user.getId())
                .orElseThrow()
                .getLastActiveAt() != null);
    }

    @Test
    void nonMemberCannotReadOrSendToRandomRoom() throws Exception {
        User member = createUser("room-member");
        User stranger = createUser("room-stranger");
        Long roomId = matchmakingService.join(member).getChatRoom().getId();

        mockMvc.perform(get("/api/messages/chatroom/{roomId}", roomId)
                        .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatRoomId\":" + roomId + ",\"content\":\"must not leak\"}")
                        .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void randomJoinRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/chatrooms/random-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private User createUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + suffix);
        user.setEmail(prefix + "-" + suffix + "@test.local");
        user.setPasswordHash("test-hash");
        user.setRole(Role.USER);
        user.setPremium(false);
        return userRepository.save(user);
    }

    private String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
    }
}
