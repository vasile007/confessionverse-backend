package com.confessionverse.backend.security.ownership;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.ConfessionVoteRepository;
import com.confessionverse.backend.repository.MessageRepository;
import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.SubscriptionService;
import com.confessionverse.backend.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("ownershipUtil")
public class OwnershipUtil {

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ConfessionRepository confessionRepository;
    private final ConfessionVoteRepository confessionVoteRepository;
    private final ChatRoomService chatRoomService;

    public OwnershipUtil(UserService userService,
                         SubscriptionService subscriptionService,
                         MessageRepository messageRepository,
                         ChatRoomRepository chatRoomRepository,
                         ConfessionRepository confessionRepository,
                         ConfessionVoteRepository confessionVoteRepository,
                         ChatRoomService chatRoomService) {
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.messageRepository = messageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.confessionRepository = confessionRepository;
        this.confessionVoteRepository = confessionVoteRepository;
        this.chatRoomService = chatRoomService;
    }

    public boolean isAdmin(String email) {
        return userService.getRolesByEmail(email).contains("ROLE_ADMIN");
    }

    public boolean isOwner(Long userId, String email) {
        Long ownerId = userService.getUserIdByEmail(email);
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean isOwnerBySubscriptionId(Long subscriptionId, String email) {
        Long ownerId = subscriptionService.getOwnerUserIdBySubscriptionId(subscriptionId);
        Long currentUserId = userService.getUserIdByEmail(email);
        return currentUserId != null && currentUserId.equals(ownerId);
    }

    public boolean hasAccessToMessage(Long messageId, String email) {
        Long userId = userService.getUserIdByEmail(email);
        if (userId == null) return false;

        return messageRepository.findById(messageId)
                .map(message -> {
                    Long messageOwnerId = message.getSender() != null ? message.getSender().getId() : null;
                    return userId.equals(messageOwnerId);
                })
                .orElse(false);
    }

    // Recommended version: more correct and simpler
    public boolean isParticipantInChatRoom(Long chatRoomId, String email) {
        Long userId = userService.getUserIdByEmail(email);
        if (userId == null) {
            return false;
        }
        return chatRoomService.isActiveParticipant(chatRoomId, userId);
    }

    public boolean isCreatorOfChatRoom(Long chatRoomId, String email) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
        if (chatRoom == null || chatRoom.getCreator() == null) return false;

        return chatRoom.getCreator().getEmail().equals(email);
    }

    public boolean checkOwnership(Class<?> entityClass, Long entityId, String email) {
        if (email == null || entityId == null || entityClass == null) {
            return false;
        }

        if (isAdmin(email)) {
            return true; // admin are acces peste tot
        }

        Long currentUserId = userService.getUserIdByEmail(email);
        if (currentUserId == null) {
            return false;
        }

        if (entityClass.equals(Confession.class)) {
            return confessionRepository.findById(entityId)
                    .map(confession -> {
                        if (confession.getUser() == null) {
                            return false;
                        }
                        return confession.getUser().getId().equals(currentUserId);
                    })
                    .orElse(false);
        }

        if (entityClass.equals(Subscription.class)) {
            Long ownerId = subscriptionService.getOwnerUserIdBySubscriptionId(entityId);
            return ownerId != null && ownerId.equals(currentUserId);
        }

        if (entityClass.equals(Message.class)) {
            return messageRepository.findById(entityId)
                    .map(message -> {
                        if (message.getSender() == null) return false;
                        return message.getSender().getId().equals(currentUserId);
                    }).orElse(false);
        }

        if (entityClass.equals(ChatRoom.class)) {
            // Check whether user is a participant or creator in the chatroom
            Optional<ChatRoom> optionalChatRoom = chatRoomRepository.findById(entityId);
            if (optionalChatRoom.isEmpty()) {
                return false;
            }
            ChatRoom chatRoom = optionalChatRoom.get();

            boolean isCreator = chatRoom.getCreator() != null && chatRoom.getCreator().getId().equals(currentUserId);
            boolean isParticipant = chatRoomService.isActiveParticipant(chatRoom.getId(), currentUserId);

            return isCreator || isParticipant;
        }

        // If entityClass is none of the above, return false
        return false;
    }

    public boolean isVoteOwner(Long voteId, Authentication auth) {
        String username = auth.getName();
        return confessionVoteRepository.findById(voteId)
                .map(v -> v.getVoterIp().equals(username)) // or another ownership criterion
                .orElse(false);
    }
}
