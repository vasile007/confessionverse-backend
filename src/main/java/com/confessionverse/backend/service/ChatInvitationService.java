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
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatInvitationRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final UserRepository userRepository;
    private final FreePlanLimitService freePlanLimitService;

    public ChatInvitationService(ChatInvitationRepository chatInvitationRepository,
                                 ChatRoomRepository chatRoomRepository,
                                 ChatRoomMembershipRepository chatRoomMembershipRepository,
                                 UserService userService,
                                 ChatRoomService chatRoomService,
                                 SimpMessagingTemplate messagingTemplate,
                                 ChatInvitationProperties chatInvitationProperties,
                                 UserRepository userRepository,
                                 FreePlanLimitService freePlanLimitService) {
        this.chatInvitationRepository = chatInvitationRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMembershipRepository = chatRoomMembershipRepository;
        this.userService = userService;
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.chatInvitationProperties = chatInvitationProperties;
        this.userRepository = userRepository;
        this.freePlanLimitService = freePlanLimitService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChatInvitationActionResponseDTO requestPrivateConversation(String inviterEmail, String inviteeUsername) {
        User inviter = userService.getUserEntityByEmail(inviterEmail);
        User invitee = userService.getUserEntityByUsername(inviteeUsername);
        if (inviter.getId().equals(invitee.getId())) {
            throw new IllegalArgumentException("You cannot invite yourself");
        }

        // The lower user id is the canonical mutex for this unordered pair. Every
        // A/B or B/A request locks the exact same row before inspecting or writing
        // rooms/invitations, avoiding multi-row lock-order deadlocks.
        Long pairLockUserId = Math.min(inviter.getId(), invitee.getId());
        userRepository.findByIdForUpdate(pairLockUserId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Private chat user not found"));
        // Run the global expiration update only after the pair mutex is held;
        // doing it first can acquire invitation gap locks in opposite order to
        // the user/invitation writes and deadlock crossed requests.
        expirePendingInvitations();

        ChatRoom acceptedRoom = chatRoomService.findAcceptedPrivateRoom(inviter.getId(), invitee.getId())
                .orElse(null);
        if (acceptedRoom != null) {
            return new ChatInvitationActionResponseDTO(
                    "Private conversation already exists.",
                    null,
                    ChatInvitationStatus.ACCEPTED.name(),
                    acceptedRoom.getId(),
                    null,
                    chatRoomService.toSummaryDto(acceptedRoom));
        }

        ChatInvitation pending = chatInvitationRepository.findPairInvitationsByStatus(
                        inviter.getId(), invitee.getId(), ChatInvitationStatus.PENDING)
                .stream()
                .filter(invitation -> !isExpired(invitation))
                .findFirst()
                .orElse(null);
        if (pending != null) {
            boolean requesterCanRespond = pending.getInvitee() != null
                    && inviter.getId().equals(pending.getInvitee().getId());
            return new ChatInvitationActionResponseDTO(
                    requesterCanRespond
                            ? "A private request from this user is already waiting for your response."
                            : "Private chat request already pending.",
                    pending.getId(),
                    pending.getStatus().name(),
                    pending.getChatRoom().getId(),
                    toDto(pending),
                    null);
        }

        freePlanLimitService.enforceConversationCreateLimit(inviter);
        ChatRoom room = chatRoomService.createGroupRoom(inviterEmail, null, null, ChatRoomType.DIRECT);
        ChatInvitationActionResponseDTO created = createInvite(room.getId(), inviterEmail, inviteeUsername);
        chatRoomService.deactivateMembership(room.getId(), inviter.getId());
        return new ChatInvitationActionResponseDTO(
                created.getMessage(),
                created.getInviteId(),
                created.getStatus(),
                room.getId(),
                created.getInvitation(),
                chatRoomService.toSummaryDto(room));
    }

    @Transactional
    public ChatInvitationActionResponseDTO createInvite(Long chatRoomId, String inviterEmail, String usernameToAdd) {
        expirePendingInvitations();
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("ChatRoom not found"));
        User inviter = userService.getUserEntityByEmail(inviterEmail);
        User invitee = userService.getUserEntityByUsername(usernameToAdd);

        if (chatRoom.getRoomType() == ChatRoomType.STANDARD
                || chatRoom.getRoomType() == ChatRoomType.RANDOM
                || (chatRoom.getUsername() != null && chatRoom.getUsername().startsWith("Random "))) {
            throw new ForbiddenInviteException("Start a separate private chat request instead of inviting into this room.");
        }

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
        String inviteeEmail = invitation.getInvitee().getEmail();
        Long invitationId = invitation.getId();
        dispatchAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                inviteeEmail,
                "/queue/chat-invites",
                Map.of("event", "CHAT_INVITE_CREATED", "invitationId", invitationId)));
    }

    private void notifyInviteAccepted(ChatInvitation invitation) {
        Long invitationId = invitation.getId();
        Long chatRoomId = invitation.getChatRoom().getId();
        if (invitation.getInviter() != null && invitation.getInviter().getEmail() != null) {
            String inviterEmail = invitation.getInviter().getEmail();
            dispatchAfterCommit(() -> {
                messagingTemplate.convertAndSendToUser(
                        inviterEmail,
                        "/queue/chat-invites",
                        Map.of("event", "CHAT_INVITE_ACCEPTED", "invitationId", invitationId));
                messagingTemplate.convertAndSendToUser(
                        inviterEmail,
                        "/queue/chatrooms",
                        Map.of("event", "CHATROOM_REFRESH", "chatRoomId", chatRoomId));
            });
        }
        if (invitation.getInvitee() != null && invitation.getInvitee().getEmail() != null) {
            String inviteeEmail = invitation.getInvitee().getEmail();
            dispatchAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                    inviteeEmail,
                    "/queue/chatrooms",
                    Map.of("event", "CHATROOM_REFRESH", "chatRoomId", chatRoomId)));
        }
    }

    private void notifyInviteDeclined(ChatInvitation invitation) {
        if (invitation.getInviter() == null || invitation.getInviter().getEmail() == null) {
            return;
        }
        String inviterEmail = invitation.getInviter().getEmail();
        Long invitationId = invitation.getId();
        dispatchAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                inviterEmail,
                "/queue/chat-invites",
                Map.of("event", "CHAT_INVITE_DECLINED", "invitationId", invitationId)));
    }

    private void dispatchAfterCommit(Runnable notification) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            notification.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notification.run();
            }
        });
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
