package com.confessionverse.backend;

import com.confessionverse.backend.dto.requestDTO.VoteRequestDto;
import com.confessionverse.backend.dto.responseDTO.VoteResponseDto;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.model.VoteType;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.service.ConfessionVoteService;
import com.confessionverse.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfessionVoteFlowIntegrationTest {

    @Autowired
    private ConfessionVoteService confessionVoteService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConfessionRepository confessionRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void authenticatedUsersCanVoteLikeAndDislike() {
        Confession confession = createConfession();
        User likeUser = createUser("vote-like");
        User dislikeUser = createUser("vote-dislike");

        VoteResponseDto likeResponse = confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.LIKE),
                likeUser
        );

        VoteResponseDto dislikeResponse = confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.DISLIKE),
                dislikeUser
        );

        assertTrue(Boolean.TRUE.equals(likeResponse.getLiked()));
        assertTrue(Boolean.FALSE.equals(dislikeResponse.getLiked()));
        assertEquals(1, dislikeResponse.getLikeCount());
        assertEquals(1, dislikeResponse.getDislikeCount());
    }

    @Test
    void changingVoteFromLikeToDislikeShouldUpdateExistingVote() {
        Confession confession = createConfession();
        User user = createUser("vote-switch");

        confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.LIKE),
                user
        );

        VoteResponseDto switched = confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.DISLIKE),
                user
        );

        assertTrue(Boolean.FALSE.equals(switched.getLiked()));
        assertEquals(0, switched.getLikeCount());
        assertEquals(1, switched.getDislikeCount());
    }

    @Test
    void duplicateVoteInSameDirectionShouldReturnExpectedMessage() {
        Confession confession = createConfession();
        User user = createUser("vote-duplicate");

        confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.LIKE),
                user
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> confessionVoteService.vote(
                        confession.getId(),
                        "127.0.0.1",
                        new VoteRequestDto(VoteType.LIKE),
                        user
                )
        );

        assertEquals("You already voted this way", ex.getMessage());
    }

    @Test
    void userVoteEndpointShouldReturnLikedFlag() throws Exception {
        User user = ensureUserWithEmail("vote-endpoint", "vote-endpoint@test.local");
        Confession confession = createConfession();
        String token = jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());

        VoteResponseDto response = confessionVoteService.vote(
                confession.getId(),
                "127.0.0.1",
                new VoteRequestDto(VoteType.LIKE),
                user
        );
        assertNotNull(response);

        mockMvc.perform(get("/api/votes/{confessionId}/user-vote", confession.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confessionId").value(confession.getId()))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.dislikeCount").value(0));
    }

    private Confession createConfession() {
        User author = createUser("confession-author");
        Confession confession = new Confession();
        confession.setUser(author);
        confession.setContent("vote test confession " + UUID.randomUUID());
        return confessionRepository.save(confession);
    }

    private User createUser(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return ensureUserWithEmail(prefix + "-" + uid, prefix + "-" + uid + "@test.local");
    }

    private User ensureUserWithEmail(String username, String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash("test-hash");
            user.setRole(Role.USER);
            return userRepository.save(user);
        });
    }
}
