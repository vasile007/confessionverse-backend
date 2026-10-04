package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.ChatMessageDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.ChatMessageMapper;
import com.confessionverse.backend.mapper.MessageMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.MessageRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.FreePlanLimitService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class ChatWebSocketController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FreePlanLimitService freePlanLimitService;
    private final ChatRoomMembershipRepository chatRoomMembershipRepository;
    private final MessageMapper messageMapper;

    public ChatWebSocketController(MessageRepository messageRepository,
                                   UserRepository userRepository,
                                   ChatRoomService chatRoomService,
                                   SimpMessagingTemplate messagingTemplate,
                                   FreePlanLimitService freePlanLimitService,
                                   ChatRoomMembershipRepository chatRoomMembershipRepository,
                                   MessageMapper messageMapper) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.freePlanLimitService = freePlanLimitService;
        this.chatRoomMembershipRepository = chatRoomMembershipRepository;
        this.messageMapper = messageMapper;
    }

    @MessageMapping("/chat.send")
    @Transactional
    public void sendMessage(ChatMessageDTO dto, Principal principal) {
        String senderEmail = principal != null ? principal.getName() : null;
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new RuntimeException("Unauthorized: Missing user identity");
        }

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + senderEmail));
        freePlanLimitService.enforceMessageSendLimit(sender);

        Long chatRoomId;
        try {
            chatRoomId = Long.parseLong(dto.getChatRoomId());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid chatRoomId: " + dto.getChatRoomId());
        }

        ChatRoom chatRoom = chatRoomService.getChatRoomEntityById(chatRoomId);
        chatRoomService.enforcePremiumRoomAccess(sender, chatRoom);
        if (sender.getRole() != Role.ADMIN && !chatRoomService.isActiveParticipant(chatRoomId, sender.getId())) {
            throw new SecurityException("Only active participants can send messages in this chat");
        }

        Message message = ChatMessageMapper.toEntity(dto, sender, chatRoom);
        message.setTimestamp(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        chatRoomMembershipRepository.findAllByChatRoom_IdAndActiveTrue(chatRoomId).stream()
                .map(membership -> membership.getUser())
                .filter(java.util.Objects::nonNull)
                .forEach(participant -> messagingTemplate.convertAndSendToUser(
                        participant.getEmail(), "/queue/messages", messageMapper.toResponseDTO(saved)));
    }

    @MessageMapping("/chat.private")
    @Transactional
    public void sendPrivateMessage(ChatMessageDTO dto, Principal principal) {
        String senderEmail = principal != null ? principal.getName() : null;
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new RuntimeException("Unauthorized: Missing user identity");
        }

        String receiverUsername = dto.getReceiver();
        if (receiverUsername == null || receiverUsername.isBlank()) {
            throw new IllegalArgumentException("Receiver username is missing for private message.");
        }

        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found: " + senderEmail));
        freePlanLimitService.enforceMessageSendLimit(sender);
        User receiver = userRepository.findByUsername(receiverUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found: " + receiverUsername));

        ChatRoom chatRoom = chatRoomService.findAcceptedPrivateRoom(sender.getId(), receiver.getId())
                .orElseThrow(() -> new SecurityException("A private chat invitation must be accepted before messaging"));
        if (!chatRoomService.isActiveParticipant(chatRoom.getId(), sender.getId())
                || !chatRoomService.isActiveParticipant(chatRoom.getId(), receiver.getId())) {
            throw new SecurityException("Both users must be active participants in this private chat");
        }
        chatRoomService.enforcePremiumRoomAccess(sender, chatRoom);

        Message message = ChatMessageMapper.toEntity(dto, sender, chatRoom);
        message.setTimestamp(LocalDateTime.now());
        Message saved = messageRepository.save(message);
        var response = messageMapper.toResponseDTO(saved);

        messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/messages", response);
    }

    @MessageExceptionHandler({IllegalArgumentException.class, SecurityException.class, ResourceNotFoundException.class})
    @SendToUser(destinations = "/queue/chat-errors", broadcast = false)
    public Map<String, String> handleChatMessageError(Exception exception) {
        return Map.of("error", exception.getMessage() != null
                ? exception.getMessage()
                : "The chat action could not be completed.");
    }
}
