package com.confessionverse.backend.service;

import com.confessionverse.backend.config.ChatInvitationProperties;
import com.confessionverse.backend.dto.responseDTO.ChatInvitationActionResponseDTO;
import com.confessionverse.backend.dto.responseDTO.ChatInvitationDTO;
import com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO;
import com.confessionverse.backend.exception.ForbiddenInviteException;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.model.ChatInvitation;
import com.confessionverse.backend.model.ChatInvitationStatus;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatInvitationRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ChatInvitationService {
    private static final Logger log = LoggerFactory.getLogger(ChatInvitationService.class);

    private final ChatInvitationRepository chatInvitationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMembershipRepository chatRoomMembershipRepository;
    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatInvitationProperties chatInvitationProperties;

    public ChatInvitationService(ChatInvitationRepository chatInvitationRepository,
                                 ChatRoomRepository chatRoomRepository,
                                 ChatRoomMembershipRepository chatRoomMembershipRepository,
                                 UserService userService,
                                 ChatRoomService chatRoomService,
                                 SimpMessagingTemplate messagingTemplate,
                                 ChatInvitationProperties chatInvitationProperties) {
        this.chatInvitationRepository = chatInvitationRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMembershipRepository = chatRoomMembershipRepository;
        this.userService = userService;
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.chatInvitationProperties = chatInvitationProperties;
    }

    @Transactional
    public ChatInvitationActionResponseDTO createInvite(Long chatRoomId, String inviterEmail, String usernameToAdd) {
        expirePendingInvitations();
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("ChatRoom not found"));
        User inviter = userService.getUserEntityByEmail(inviterEmail);
        User invitee = userService.getUserEntityByUsername(usernameToAdd);

        boolean isAdmin = inviter.getRole() == Role.ADMIN;
        boolean isParticipant = chatRoomMembershipRepository
                .existsByChatRoom_IdAndUser_IdAndActiveTrue(chatRoomId, inviter.getId());
        if (!isAdmin && !isParticipant) {
            throw new ForbiddenInviteException("Only chat participants or admin can invite users.");
        }
        chatRoomService.enforcePremiumRoomAccess(inviter, chatRoom);
        chatRoomService.enforcePremiumRoomAccess(invitee, chatRoom);
        if (chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(chatRoomId, invitee.getId())) {
            return new ChatInvitationActionResponseDTO(
                    "User is already a participant.",
                    null,
                    null,
                    chatRoom.getId(),
                    null,
                    chatRoomService.toSummaryDto(chatRoom)
            );
        }

        ChatInvitation existingPending = chatInvitationRepository
                .findFirstByChatRoom_IdAndInvitee_IdAndStatus(chatRoomId, invitee.getId(), ChatInvitationStatus.PENDING)
                .orElse(null);
        if (existingPending != null) {
            return new ChatInvitationActionResponseDTO(
                    "Invitation already pending.",
                    existingPending.getId(),
                    existingPending.getStatus().name(),
                    chatRoom.getId(),
                    toDto(existingPending),
                    null
            );
        }

        ChatInvitation invitation = new ChatInvitation();
        invitation.setChatRoom(chatRoom);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(ChatInvitationStatus.PENDING);
        ChatInvitation saved = chatInvitationRepository.save(invitation);

        notifyInviteCreated(saved);
        return new ChatInvitationActionResponseDTO(
                "Invitation created.",
                saved.getId(),
                saved.getStatus().name(),
                chatRoom.getId(),
                toDto(saved),
                null
        );
    }

    @Transactional
    public List<ChatInvitationDTO> getPendingInvitesForCurrentUser(String inviteeEmail) {
        expirePendingInvitations();
        return chatInvitationRepository.findByInvitee_EmailAndStatusOrderByCreatedAtDesc(inviteeEmail, ChatInvitationStatus.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ChatInvitationActionResponseDTO acceptInvitation(Long inviteId, String inviteeEmail) {
        expirePendingInvitations();
        ChatInvitation invitation = getInvitationForInvitee(inviteId, inviteeEmail);
        if (invitation.getStatus() == ChatInvitationStatus.ACCEPTED) {
            ChatRoom acceptedRoom = invitation.getChatRoom();
            if (acceptedRoom != null && invitation.getInviter() != null && invitation.getInvitee() != null) {
                chatRoomService.ensureMembershipExists(acceptedRoom.getId(), invitation.getInviter().getId());
                chatRoomService.ensureMembershipExists(acceptedRoom.getId(), invitation.getInvitee().getId());
            }
            return new ChatInvitationActionResponseDTO(
                    "Invitation already accepted.",
                    invitation.getId(),
                    invitation.getStatus().name(),
                    acceptedRoom != null ? acceptedRoom.getId() : null,
                    toDto(invitation),
                    acceptedRoom != null ? chatRoomService.toSummaryDto(acceptedRoom) : null
            );
        }
        ensurePending(invitation);

        ChatRoom room = invitation.getChatRoom();
        User inviter = invitation.getInviter();
        User invitee = invitation.getInvitee();
        if (room == null || inviter == null || invitee == null) {
            throw new IllegalArgumentException("Invitation is missing required room or users.");
        }

        chatRoomService.enforcePremiumRoomAccess(invitee, room);

        boolean inviterParticipantAdded = !chatRoomMembershipRepository
                .existsByChatRoom_IdAndUser_IdAndActiveTrue(room.getId(), inviter.getId());
        boolean inviteeParticipantAdded = !chatRoomMembershipRepository
                .existsByChatRoom_IdAndUser_IdAndActiveTrue(room.getId(), invitee.getId());

        chatRoomService.activateMembership(room.getId(), inviter.getId());
        chatRoomService.activateMembership(room.getId(), invitee.getId());
        room = chatRoomRepository.findById(room.getId())
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("ChatRoom not found"));

        invitation.setStatus(ChatInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        ChatInvitation saved = chatInvitationRepository.save(invitation);

        notifyInviteAccepted(saved);
        ChatRoomSummaryDTO roomSummary = chatRoomService.toSummaryDto(room);
        log.info("invite.accept inviteId={} inviteeUserId={} chatRoomId={} participantAdded={}",
                saved.getId(),
                invitee.getId(),
                room.getId(),
                inviterParticipantAdded || inviteeParticipantAdded);
        return new ChatInvitationActionResponseDTO(
                "Invitation accepted.",
                saved.getId(),
                saved.getStatus().name(),
                room.getId(),
                toDto(saved),
                roomSummary
        );
    }

    @Transactional
    public int backfillAcceptedInviteMemberships() {
        int fixed = 0;
        List<ChatInvitation> acceptedInvites = chatInvitationRepository.findByStatus(ChatInvitationStatus.ACCEPTED);
        for (ChatInvitation invitation : acceptedInvites) {
            ChatRoom room = invitation.getChatRoom();
            User inviter = invitation.getInviter();
            User invitee = invitation.getInvitee();
            if (room == null || inviter == null || invitee == null) {
                continue;
            }
            boolean inviterCreated = chatRoomService.ensureMembershipExists(room.getId(), inviter.getId());
            boolean inviteeCreated = chatRoomService.ensureMembershipExists(room.getId(), invitee.getId());
            if (inviterCreated) {
                fixed++;
            }
            if (inviteeCreated) {
                fixed++;
            }
        }
        return fixed;
    }

    @Transactional
    public ChatInvitationActionResponseDTO declineInvitation(Long inviteId, String inviteeEmail) {
        expirePendingInvitations();
        ChatInvitation invitation = getInvitationForInvitee(inviteId, inviteeEmail);
        ensurePending(invitation);

        invitation.setStatus(ChatInvitationStatus.DECLINED);
        invitation.setRespondedAt(LocalDateTime.now());
        ChatInvitation saved = chatInvitationRepository.save(invitation);
        notifyInviteDeclined(saved);

        return new ChatInvitationActionResponseDTO(
                "Invitation declined.",
                saved.getId(),
                saved.getStatus().name(),
                saved.getChatRoom() != null ? saved.getChatRoom().getId() : null,
                toDto(saved),
                null
        );
    }

    private ChatInvitation getInvitationForInvitee(Long inviteId, String inviteeEmail) {
        ChatInvitation invitation = chatInvitationRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Invitation not found"));
        if (invitation.getInvitee() == null || invitation.getInvitee().getEmail() == null
                || !invitation.getInvitee().getEmail().equalsIgnoreCase(inviteeEmail)) {
            throw new ForbiddenInviteException("Only invitee can respond to this invitation.");
        }
        return invitation;
    }

    private void ensurePending(ChatInvitation invitation) {
        if (invitation.getStatus() != ChatInvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is not pending.");
        }
        if (isExpired(invitation)) {
            invitation.setStatus(ChatInvitationStatus.EXPIRED);
            invitation.setRespondedAt(LocalDateTime.now());
            chatInvitationRepository.save(invitation);
            throw new IllegalArgumentException("Invitation expired.");
        }
    }

    private ChatInvitationDTO toDto(ChatInvitation invitation) {
        return new ChatInvitationDTO(
                invitation.getId(),
                invitation.getChatRoom() != null ? invitation.getChatRoom().getId() : null,
                invitation.getInviter() != null ? invitation.getInviter().getUsername() : null,
                invitation.getInvitee() != null ? invitation.getInvitee().getUsername() : null,
                invitation.getStatus() != null ? invitation.getStatus().name() : null,
                invitation.getCreatedAt(),
                invitation.getRespondedAt()
        );
    }

    private void notifyInviteCreated(ChatInvitation invitation) {
        if (invitation.getInvitee() == null || invitation.getInvitee().getEmail() == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                invitation.getInvitee().getEmail(),
                "/queue/chat-invites",
                Map.of("event", "CHAT_INVITE_CREATED", "invitationId", invitation.getId())
        );
    }

    private void notifyInviteAccepted(ChatInvitation invitation) {
        if (invitation.getInviter() != null && invitation.getInviter().getEmail() != null) {
            messagingTemplate.convertAndSendToUser(
                    invitation.getInviter().getEmail(),
                    "/queue/chat-invites",
                    Map.of("event", "CHAT_INVITE_ACCEPTED", "invitationId", invitation.getId())
            );
            messagingTemplate.convertAndSendToUser(
                    invitation.getInviter().getEmail(),
                    "/queue/chatrooms",
                    Map.of("event", "CHATROOM_REFRESH", "chatRoomId", invitation.getChatRoom().getId())
            );
        }
        if (invitation.getInvitee() != null && invitation.getInvitee().getEmail() != null) {
            messagingTemplate.convertAndSendToUser(
                    invitation.getInvitee().getEmail(),
                    "/queue/chatrooms",
                    Map.of("event", "CHATROOM_REFRESH", "chatRoomId", invitation.getChatRoom().getId())
            );
        }
    }

    private void notifyInviteDeclined(ChatInvitation invitation) {
        if (invitation.getInviter() == null || invitation.getInviter().getEmail() == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                invitation.getInviter().getEmail(),
                "/queue/chat-invites",
                Map.of("event", "CHAT_INVITE_DECLINED", "invitationId", invitation.getId())
        );
    }

    @Transactional
    public int expirePendingInvitations() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(chatInvitationProperties.getTtlHours());
        return chatInvitationRepository.markPendingAsExpired(
                ChatInvitationStatus.PENDING,
                ChatInvitationStatus.EXPIRED,
                cutoff,
                LocalDateTime.now()
        );
    }

    private boolean isExpired(ChatInvitation invitation) {
        if (invitation.getCreatedAt() == null) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(chatInvitationProperties.getTtlHours());
        return invitation.getCreatedAt().isBefore(cutoff);
    }
}
