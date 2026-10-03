package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO;
import com.confessionverse.backend.dto.responseDTO.RandomChatMatchDTO;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class RandomChatMatchmakingService {
    private static final Duration WAITING_TTL = Duration.ofMinutes(5);

    private final Map<ChatRoomType, ArrayDeque<WaitingUser>> queues = new EnumMap<>(ChatRoomType.class);
    private final Map<Long, WaitingUser> waitingByUser = new HashMap<>();
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
    public synchronized RandomChatMatchDTO join(User user, ChatRoomType roomType) {
        validateCategory(roomType);
        chatRoomService.enforcePremiumRoomAccess(user, roomType);
        removeWaitingUser(user.getId());
        removeExpired();

        ChatRoom existingConversation = membershipRepository
                .findAllByUser_IdAndActiveTrueAndHiddenAtIsNull(user.getId()).stream()
                .map(membership -> membership.getChatRoom())
                .filter(room -> room != null
                        && roomType == room.getRoomType()
                        && room.getUsername() != null
                        && room.getUsername().startsWith("Random ")
                        && membershipRepository.findAllByChatRoom_IdAndActiveTrue(room.getId()).size() == 2)
                .findFirst()
                .orElse(null);
        if (existingConversation != null) {
            return new RandomChatMatchDTO(
                    "MATCHED", roomType.name(), chatRoomService.toSummaryDto(existingConversation));
        }

        ArrayDeque<WaitingUser> queue = queues.computeIfAbsent(roomType, ignored -> new ArrayDeque<>());
        WaitingUser partner = null;
        while (!queue.isEmpty() && partner == null) {
            WaitingUser candidate = queue.removeFirst();
            waitingByUser.remove(candidate.user().getId());
            if (!candidate.user().getId().equals(user.getId())) {
                partner = candidate;
            }
        }

        if (partner == null) {
            WaitingUser waiting = new WaitingUser(user, roomType, Instant.now(clock));
            queue.addLast(waiting);
            waitingByUser.put(user.getId(), waiting);
            return new RandomChatMatchDTO("WAITING", roomType.name(), null);
        }

        ChatRoom room = new ChatRoom();
        room.setCreator(partner.user());
        room.setRoomType(roomType);
        room.setUsername("Random " + categoryName(roomType));
        room = chatRoomRepository.save(room);
        chatRoomService.activateMembership(room.getId(), partner.user().getId());
        chatRoomService.activateMembership(room.getId(), user.getId());

        ChatRoomSummaryDTO summary = chatRoomService.toSummaryDto(room);
        RandomChatMatchDTO result = new RandomChatMatchDTO("MATCHED", roomType.name(), summary);
        messagingTemplate.convertAndSendToUser(partner.user().getEmail(), "/queue/random-chat", result);
        messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/random-chat", result);
        return result;
    }

    public synchronized void cancel(Long userId) {
        removeWaitingUser(userId);
    }

    public synchronized void disconnect(String email) {
        if (email == null) return;
        waitingByUser.values().stream()
                .filter(waiting -> email.equalsIgnoreCase(waiting.user().getEmail()))
                .map(waiting -> waiting.user().getId())
                .findFirst()
                .ifPresent(this::removeWaitingUser);
    }

    @Transactional
    public synchronized boolean leaveConversation(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null || room.getUsername() == null || !room.getUsername().startsWith("Random ")) {
            return false;
        }
        if (!membershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(roomId, userId)) {
            throw new AccessDeniedException("Only participants can leave this chat");
        }
        membershipRepository.findAllByChatRoom_IdAndActiveTrue(roomId).forEach(membership -> {
            membership.setActive(false);
            membership.setLeftAt(java.time.LocalDateTime.now(clock));
            membershipRepository.save(membership);
            if (membership.getUser() != null) {
                messagingTemplate.convertAndSendToUser(
                        membership.getUser().getEmail(),
                        "/queue/random-chat",
                        Map.of("status", "ENDED", "chatRoomId", roomId));
            }
        });
        return true;
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void evictExpiredWaitingUsers() {
        removeExpired();
    }

    private void removeExpired() {
        Instant cutoff = Instant.now(clock).minus(WAITING_TTL);
        Iterator<Map.Entry<Long, WaitingUser>> iterator = waitingByUser.entrySet().iterator();
        while (iterator.hasNext()) {
            WaitingUser waiting = iterator.next().getValue();
            if (waiting.joinedAt().isBefore(cutoff)) {
                ArrayDeque<WaitingUser> queue = queues.get(waiting.roomType());
                if (queue != null) queue.remove(waiting);
                iterator.remove();
            }
        }
    }

    private void removeWaitingUser(Long userId) {
        WaitingUser existing = waitingByUser.remove(userId);
        if (existing == null) return;
        ArrayDeque<WaitingUser> queue = queues.get(existing.roomType());
        if (queue != null) queue.remove(existing);
    }

    private void validateCategory(ChatRoomType roomType) {
        if (roomType != ChatRoomType.STANDARD
                && roomType != ChatRoomType.LATE_NIGHT
                && roomType != ChatRoomType.HEARTBEAT) {
            throw new IllegalArgumentException("Unsupported random chat room type");
        }
        if (roomType == ChatRoomType.LATE_NIGHT && !chatRoomService.isLateNightAvailable()) {
            throw new AccessDeniedException("Late Night is active only at night (22:00 - 06:00).");
        }
    }

    private String categoryName(ChatRoomType roomType) {
        return switch (roomType) {
            case STANDARD -> "General";
            case LATE_NIGHT -> "Late Night";
            case HEARTBEAT -> "Premium Room";
            default -> roomType.name();
        };
    }

    private record WaitingUser(User user, ChatRoomType roomType, Instant joinedAt) {}
}
