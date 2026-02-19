package com.confessionverse.backend.service;

import com.confessionverse.backend.config.ChatInvitationProperties;
import com.confessionverse.backend.dto.responseDTO.ChatInvitationActionResponseDTO;
import com.confessionverse.backend.exception.PremiumRoomRequiredException;
import com.confessionverse.backend.model.*;
import com.confessionverse.backend.repository.ChatInvitationRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatInvitationServiceTest {

    @Mock
    private ChatInvitationRepository chatInvitationRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMembershipRepository chatRoomMembershipRepository;
    @Mock
    private UserService userService;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatInvitationProperties chatInvitationProperties;

    private ChatInvitationService service;

    @BeforeEach
    void setUp() {
        service = new ChatInvitationService(
                chatInvitationRepository,
                chatRoomRepository,
                chatRoomMembershipRepository,
                userService,
                chatRoomService,
                messagingTemplate,
                chatInvitationProperties
        );
        when(chatInvitationProperties.getTtlHours()).thenReturn(24L);
    }

    @Test
    void createInviteReturnsIdempotentWhenAlreadyParticipant() {
        User inviter = user(1L, "alice", "alice@test.com");
        User invitee = user(2L, "bob", "bob@test.com");
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setParticipants(new LinkedHashSet<>());
        room.getParticipants().add(inviter);
        room.getParticipants().add(invitee);

        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userService.getUserEntityByEmail("alice@test.com")).thenReturn(inviter);
        when(userService.getUserEntityByUsername("bob")).thenReturn(invitee);
        when(chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(10L, 1L)).thenReturn(true);
        when(chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(10L, 2L)).thenReturn(true);

        ChatInvitationActionResponseDTO result = service.createInvite(10L, "alice@test.com", "bob");

        assertEquals("User is already a participant.", result.getMessage());
        assertNull(result.getInvitation());
        verify(chatInvitationRepository, never()).save(any());
    }

    @Test
    void acceptInviteChecksPremiumGateBeforeAddingParticipant() {
        User inviter = user(1L, "alice", "alice@test.com");
        User invitee = user(2L, "bob", "bob@test.com");
        ChatRoom room = new ChatRoom();
        room.setId(11L);
        room.setRoomType(ChatRoomType.HEARTBEAT);
        room.setParticipants(new LinkedHashSet<>());
        room.getParticipants().add(inviter);

        ChatInvitation invitation = new ChatInvitation();
        invitation.setId(100L);
        invitation.setChatRoom(room);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(ChatInvitationStatus.PENDING);

        when(chatInvitationRepository.findById(100L)).thenReturn(Optional.of(invitation));
        doThrow(new PremiumRoomRequiredException("This room is available for PRO members only."))
                .when(chatRoomService).enforcePremiumRoomAccess(invitee, room);

        assertThrows(PremiumRoomRequiredException.class, () -> service.acceptInvitation(100L, "bob@test.com"));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void createInviteReturnsExistingPendingWithoutCreatingDuplicate() {
        User inviter = user(1L, "alice", "alice@test.com");
        User invitee = user(2L, "bob", "bob@test.com");
        ChatRoom room = new ChatRoom();
        room.setId(10L);
        room.setRoomType(ChatRoomType.DIRECT);
        room.setParticipants(new LinkedHashSet<>());
        room.getParticipants().add(inviter);

        ChatInvitation pending = new ChatInvitation();
        pending.setId(99L);
        pending.setChatRoom(room);
        pending.setInviter(inviter);
        pending.setInvitee(invitee);
        pending.setStatus(ChatInvitationStatus.PENDING);

        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(userService.getUserEntityByEmail("alice@test.com")).thenReturn(inviter);
        when(userService.getUserEntityByUsername("bob")).thenReturn(invitee);
        when(chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(10L, 1L)).thenReturn(true);
        when(chatInvitationRepository.findFirstByChatRoom_IdAndInvitee_IdAndStatus(10L, 2L, ChatInvitationStatus.PENDING))
                .thenReturn(Optional.of(pending));

        ChatInvitationActionResponseDTO result = service.createInvite(10L, "alice@test.com", "bob");

        assertEquals("Invitation already pending.", result.getMessage());
        assertNotNull(result.getInvitation());
        assertEquals(99L, result.getInvitation().getId());
        verify(chatInvitationRepository, never()).save(any(ChatInvitation.class));
    }

    @Test
    void acceptInviteIsIdempotentWhenAlreadyAccepted() {
        User inviter = user(1L, "alice", "alice@test.com");
        User invitee = user(2L, "bob", "bob@test.com");
        ChatRoom room = new ChatRoom();
        room.setId(12L);
        room.setRoomType(ChatRoomType.DIRECT);
        room.setParticipants(new LinkedHashSet<>());
        room.getParticipants().add(inviter);
        room.getParticipants().add(invitee);

        ChatInvitation invitation = new ChatInvitation();
        invitation.setId(101L);
        invitation.setChatRoom(room);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(ChatInvitationStatus.ACCEPTED);

        when(chatInvitationRepository.findById(101L)).thenReturn(Optional.of(invitation));
        when(chatRoomService.toSummaryDto(room)).thenReturn(new com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO());

        ChatInvitationActionResponseDTO result = service.acceptInvitation(101L, "bob@test.com");

        assertEquals("Invitation already accepted.", result.getMessage());
        assertEquals("ACCEPTED", result.getStatus());
        assertEquals(12L, result.getChatRoomId());
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void expirePendingInvitationsUsesConfiguredTtl() {
        when(chatInvitationRepository.markPendingAsExpired(
                eq(ChatInvitationStatus.PENDING),
                eq(ChatInvitationStatus.EXPIRED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))
        ).thenReturn(2);

        int expired = service.expirePendingInvitations();
        assertEquals(2, expired);
    }

    private User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(Role.USER);
        return user;
    }
}
