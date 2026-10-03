package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO;
import com.confessionverse.backend.dto.responseDTO.RandomChatMatchDTO;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatRoomMembership;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Assigns authenticated users to persistent, capacity-limited random group rooms. */
@Service
public class RandomChatMatchmakingService {
    public static final int MAX_RANDOM_ROOM_MEMBERS = 6;
    private static final Duration MEMBERSHIP_LEASE = Duration.ofMinutes(5);
    private static final List<ChatRoomType> LEGACY_RANDOM_TYPES = List.of(
            ChatRoomType.STANDARD, ChatRoomType.LATE_NIGHT, ChatRoomType.HEARTBEAT);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomMembershipRepository membershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public RandomChatMatchmakingService(ChatRoomRepository chatRoomRepository,
                                        ChatRoomService chatRoomService,
                                        ChatRoomMembershipRepository membershipRepository,
                                        SimpMessagingTemplate messagingTemplate,
                                        Clock clock) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomService = chatRoomService;
        this.membershipRepository = membershipRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @Transactional
    public RandomChatMatchDTO join(User user) {
        return assign(user, null, true);
    }

    /** Backwards-compatible signature for older callers; category selection is intentionally ignored. */
    @Transactional
    public RandomChatMatchDTO join(User user, ChatRoomType ignoredRoomType) {
        return join(user);
    }

    @Transactional
    public RandomChatMatchDTO next(User user, Long currentRoomId) {
        lockMatchmaking();
        expireStaleMemberships();
        List<ChatRoomMembership> active = activeRandomMemberships(user.getId());
        if (currentRoomId != null && active.stream()
                .noneMatch(membership -> currentRoomId.equals(membership.getChatRoom().getId()))) {
            // A repeated/late Next request returns the room assigned by the first committed request.
            if (!active.isEmpty()) {
                return joined(active.get(0).getChatRoom());
            }
        }
        Long roomToLeave = currentRoomId;
        if (roomToLeave == null && !active.isEmpty()) {
            roomToLeave = active.get(0).getChatRoom().getId();
        }
        if (roomToLeave != null) {
            leaveConversation(roomToLeave, user.getId());
        }
        return assign(user, roomToLeave, false);
    }

    private RandomChatMatchDTO assign(User user, Long excludedRoomId, boolean returnExisting) {
        // The persistent Community row is a database-backed matchmaking mutex. It serializes
        // capacity decisions across concurrent requests and backend instances.
        lockMatchmaking();
        expireStaleMemberships();

        List<ChatRoomMembership> active = activeRandomMemberships(user.getId());
        if (returnExisting && !active.isEmpty()) {
            active.stream().skip(1).forEach(this::deactivate);
            return joined(active.get(0).getChatRoom());
        }
        active.forEach(this::deactivate);

        List<ChatRoom> available = chatRoomRepository.findAvailableRandomRoomsForUpdate(
                ChatRoomType.RANDOM, excludedRoomId, MAX_RANDOM_ROOM_MEMBERS - 1L);
        ChatRoom room = available.isEmpty() ? createRandomRoom(user) : available.get(0);

        if (membershipRepository.countByChatRoom_IdAndActiveTrue(room.getId()) >= MAX_RANDOM_ROOM_MEMBERS) {
            room = createRandomRoom(user);
        }
        chatRoomService.activateMembership(room.getId(), user.getId());
        notifyRoomUpdated(room);
        return joined(room);
    }

    @Transactional
    public boolean leaveConversation(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || !isRandomRoom(room)) {
            return false;
        }
        ChatRoomMembership membership = membershipRepository
                .findByChatRoom_IdAndUser_IdAndActiveTrue(roomId, userId)
                .orElseThrow(() -> new AccessDeniedException("Only active members can leave this random chat"));
        deactivate(membership);
        if (membership.getUser() != null) {
            String email = membership.getUser().getEmail();
            afterCommit(() -> messagingTemplate.convertAndSendToUser(email, "/queue/random-chat",
                    Map.of("status", "LEFT", "chatRoomId", roomId)));
        }
        notifyRoomUpdated(room);
        return true;
    }

    @Transactional
    public void heartbeat(Long userId, Long roomId) {
        if (roomId == null) {
            throw new IllegalArgumentException("roomId is required");
        }
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AccessDeniedException("Random room is not accessible"));
        if (!isRandomRoom(room)) {
            throw new AccessDeniedException("Random room is not accessible");
        }
        int updated = membershipRepository.touchActiveMembership(roomId, userId, LocalDateTime.now(clock));
        if (updated != 1) {
            throw new AccessDeniedException("Only active members can refresh this random chat");
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupStaleMemberships() {
        lockMatchmaking();
        expireStaleMemberships();
    }

    /** Retained for API compatibility; group random chat has no waiting queue. */
    public void cancel(Long userId) {
    }

    public void disconnect(String email) {
        // Membership survives transient socket disconnects. Leaving is an explicit user action.
    }

    private ChatRoom createRandomRoom(User creator) {
        ChatRoom room = new ChatRoom();
        room.setCreator(creator);
        room.setRoomType(ChatRoomType.RANDOM);
        room.setUsername("Random Chat");
        return chatRoomRepository.saveAndFlush(room);
    }

    private List<ChatRoomMembership> activeRandomMemberships(Long userId) {
        return membershipRepository.findActiveRandomMemberships(userId, ChatRoomType.RANDOM, LEGACY_RANDOM_TYPES);
    }

    private boolean isRandomRoom(ChatRoom room) {
        return room.getRoomType() == ChatRoomType.RANDOM
                || (room.getUsername() != null && room.getUsername().startsWith("Random "));
    }

    private void deactivate(ChatRoomMembership membership) {
        membership.setActive(false);
        membership.setLeftAt(LocalDateTime.now(clock));
        membershipRepository.save(membership);
    }

    private void lockMatchmaking() {
        chatRoomService.ensureStandardRoomExists();
        chatRoomRepository.findAllByRoomTypeForUpdate(ChatRoomType.STANDARD);
    }

    private int expireStaleMemberships() {
        LocalDateTime now = LocalDateTime.now(clock);
        return membershipRepository.deactivateStaleRandomMemberships(
                ChatRoomType.RANDOM,
                LEGACY_RANDOM_TYPES,
                now.minus(MEMBERSHIP_LEASE),
                now);
    }

    private RandomChatMatchDTO joined(ChatRoom room) {
        return new RandomChatMatchDTO("JOINED", ChatRoomType.RANDOM.name(), chatRoomService.toSummaryDto(room));
    }

    private void notifyRoomUpdated(ChatRoom room) {
        ChatRoomSummaryDTO summary = chatRoomService.toSummaryDto(room);
        List<String> memberEmails = membershipRepository.findAllByChatRoom_IdAndActiveTrue(room.getId()).stream()
                .map(ChatRoomMembership::getUser)
                .filter(java.util.Objects::nonNull)
                .map(User::getEmail)
                .toList();
        afterCommit(() -> memberEmails.forEach(email -> messagingTemplate.convertAndSendToUser(
                email, "/queue/random-chat", Map.of("status", "ROOM_UPDATED", "chatRoom", summary))));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
