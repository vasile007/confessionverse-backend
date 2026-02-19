package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.ChatMessageDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.ChatMessageMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.MessageRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.FreePlanLimitService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
public class ChatWebSocketController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FreePlanLimitService freePlanLimitService;

    public ChatWebSocketController(MessageRepository messageRepository,
                                   UserRepository userRepository,
                                   ChatRoomService chatRoomService,
                                   SimpMessagingTemplate messagingTemplate,
                                   FreePlanLimitService freePlanLimitService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
        this.freePlanLimitService = freePlanLimitService;
    }

    @MessageMapping("/chat.send")
    public ChatMessageDTO sendMessage(ChatMessageDTO dto, Principal principal) {
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
        messageRepository.save(message);

        dto.setSender(sender.getUsername());
        dto.setTimestamp(message.getTimestamp().toString());
        return dto;
    }

    @MessageMapping("/chat.private")
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

        ChatRoom chatRoom = chatRoomService.getOrCreatePrivateRoomByEmails(senderEmail, receiver.getEmail());
        chatRoomService.enforcePremiumRoomAccess(sender, chatRoom);

        Message message = ChatMessageMapper.toEntity(dto, sender, chatRoom);
        message.setTimestamp(LocalDateTime.now());
        messageRepository.save(message);

        dto.setSender(sender.getUsername());
        dto.setChatRoomId(chatRoom.getId().toString());
        dto.setTimestamp(message.getTimestamp().toString());

        messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", dto);
        messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/messages", dto);
    }
}
