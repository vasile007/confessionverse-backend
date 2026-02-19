package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.requestDTO.MessageRequestDTO;
import com.confessionverse.backend.dto.responseDTO.MessageResponseDTO;
import com.confessionverse.backend.exception.NotRoomParticipantException;
import com.confessionverse.backend.mapper.MessageMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.MessageRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.OwnershipUtil;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final MessageMapper mapper;
    private final OwnershipUtil ownershipUtil;
    private final ChatRoomService chatRoomService;
    private final ChatRoomMembershipRepository chatRoomMembershipRepository;
    private final FreePlanLimitService freePlanLimitService;

    public MessageService(MessageRepository messageRepository,
                          ChatRoomRepository chatRoomRepository,
                          UserRepository userRepository,
                          MessageMapper mapper,
                          OwnershipUtil ownershipUtil,
                          ChatRoomService chatRoomService,
                          ChatRoomMembershipRepository chatRoomMembershipRepository,
                          FreePlanLimitService freePlanLimitService) {
        this.messageRepository = messageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.ownershipUtil = ownershipUtil;
        this.chatRoomService = chatRoomService;
        this.chatRoomMembershipRepository = chatRoomMembershipRepository;
        this.freePlanLimitService = freePlanLimitService;
    }

    @Transactional
    public MessageResponseDTO createMessage(MessageRequestDTO dto, Long senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new EntityNotFoundException("Sender user not found"));

        ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found"));
        if (sender.getRole() != Role.ADMIN
                && !chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(chatRoom.getId(), senderId)) {
            throw new NotRoomParticipantException("Only active participants can send messages in this chat");
        }

        chatRoomService.enforcePremiumRoomAccess(sender, chatRoom);
        freePlanLimitService.enforceMessageSendLimit(sender);

        Message message = mapper.toEntity(dto);
        message.setSender(sender);
        message.setChatRoom(chatRoom);

        Message saved = messageRepository.save(message);
        return mapper.toResponseDTO(saved);
    }

    public MessageResponseDTO getMessageById(Long messageId, Long authUserId, String authUsername) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));

        // Admin or owner check
        if (!ownershipUtil.isAdmin(authUsername) && !ownershipUtil.hasAccessToMessage(messageId, authUsername)) {
            throw new SecurityException("Access denied");
        }

        return mapper.toResponseDTO(message);
    }

    public Page<MessageResponseDTO> getMessagesByChatRoom(
            Long chatRoomId, String authUsername, Pageable pageable, String searchTerm) {

        // Check user is admin or participant
        if (!ownershipUtil.isAdmin(authUsername) && !ownershipUtil.isParticipantInChatRoom(chatRoomId, authUsername)) {
            throw new SecurityException("Access denied");
        }

        Page<Message> messagesPage;

        if (searchTerm == null || searchTerm.isBlank()) {
            messagesPage = messageRepository.findByChatRoomId(chatRoomId, pageable);
        } else {
            messagesPage = messageRepository.findByChatRoomIdAndContentContainingIgnoreCase(chatRoomId, searchTerm, pageable);
        }

        return messagesPage.map(mapper::toResponseDTO);
    }

    @Transactional
    public MessageResponseDTO updateMessage(Long id, MessageRequestDTO dto, String authUsername) {
        Message existing = messageRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Message not found"));

        if (!ownershipUtil.isAdmin(authUsername) && !ownershipUtil.hasAccessToMessage(id, authUsername)) {
            throw new SecurityException("Access denied");
        }

        existing.setContent(dto.getContent());
        // alte câmpuri editabile se setează aici

        Message saved = messageRepository.save(existing);
        return mapper.toResponseDTO(saved);
    }

    @Transactional
    public void deleteMessage(Long id, String authUsername) {
        if (!ownershipUtil.isAdmin(authUsername) && !ownershipUtil.hasAccessToMessage(id, authUsername)) {
            throw new SecurityException("Access denied");
        }
        messageRepository.deleteById(id);
    }
}









