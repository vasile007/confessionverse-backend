package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.MessageRequestDTO;
import com.confessionverse.backend.dto.responseDTO.MessageResponseDTO;
import com.confessionverse.backend.service.MessageService;
import com.confessionverse.backend.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);

    private final MessageService messageService;
    private final UserService userService;

    public MessageController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.hasAccessToMessage(#id, authentication.name)")
    public ResponseEntity<MessageResponseDTO> getMessageById(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        Long userId = userService.getUserIdByEmail(email);
        MessageResponseDTO message = messageService.getMessageById(id, userId, email);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/chatroom/{chatRoomId}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.isParticipantInChatRoom(#chatRoomId, authentication.name)")
    public ResponseEntity<Page<MessageResponseDTO>> getMessagesByChatRoom(
            @PathVariable Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            Principal principal) {

        String email = principal.getName();
        Long requesterId = userService.getUserIdByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageResponseDTO> pageResult = messageService.getMessagesByChatRoom(chatRoomId, email, pageable, search);
        log.info("messages.list chatRoomId={} requesterId={} messagesCount={}",
                chatRoomId,
                requesterId,
                pageResult.getTotalElements());
        return ResponseEntity.ok(pageResult);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponseDTO> createMessage(@Valid @RequestBody MessageRequestDTO messageRequestDTO, Principal principal) {
        String email = principal.getName();
        Long senderId = userService.getUserIdByEmail(email);
        MessageResponseDTO created = messageService.createMessage(messageRequestDTO, senderId);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.hasAccessToMessage(#id, authentication.name)")
    public ResponseEntity<MessageResponseDTO> updateMessage(@PathVariable Long id, @Valid @RequestBody MessageRequestDTO dto, Principal principal) {
        String email = principal.getName();
        MessageResponseDTO updated = messageService.updateMessage(id, dto, email);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @ownershipUtil.hasAccessToMessage(#id, authentication.name)")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        messageService.deleteMessage(id, email);
        return ResponseEntity.noContent().build();
    }
}










