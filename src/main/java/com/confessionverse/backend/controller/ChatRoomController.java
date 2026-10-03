package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.ChatRoomCreateRequestDTO;
import com.confessionverse.backend.dto.requestDTO.ChatRoomParticipantInviteRequestDTO;
import com.confessionverse.backend.dto.requestDTO.RandomChatJoinRequestDTO;
import com.confessionverse.backend.dto.responseDTO.ChatInvitationActionResponseDTO;
import com.confessionverse.backend.dto.responseDTO.MessageResponseDTO;
import com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO;
import com.confessionverse.backend.exception.PremiumRoomRequiredException;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.MessageMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatInvitationStatus;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Message;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.MessageRepository;
import com.confessionverse.backend.service.ChatInvitationService;
import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.FreePlanLimitService;
import com.confessionverse.backend.service.UserService;
import com.confessionverse.backend.service.RandomChatMatchmakingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chatrooms")
@CrossOrigin(origins = "*")
public class ChatRoomController {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomController.class);

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final ChatRoomService chatRoomService;
    private final FreePlanLimitService freePlanLimitService;
    private final ChatInvitationService chatInvitationService;
    private final MessageMapper messageMapper;
    private final RandomChatMatchmakingService matchmakingService;

    public ChatRoomController(ChatRoomRepository chatRoomRepository,
                              MessageRepository messageRepository,
                              UserService userService,
                              ChatRoomService chatRoomService,
                              FreePlanLimitService freePlanLimitService,
                              ChatInvitationService chatInvitationService,
                              MessageMapper messageMapper,
                              RandomChatMatchmakingService matchmakingService) {
        this.chatRoomRepository = chatRoomRepository;
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.chatRoomService = chatRoomService;
        this.freePlanLimitService = freePlanLimitService;
        this.chatInvitationService = chatInvitationService;
        this.messageMapper = messageMapper;
        this.matchmakingService = matchmakingService;
    }

    // ---------------- Create ChatRoom ----------------
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> create(@RequestBody ChatRoomCreateRequestDTO body, Authentication authentication) {
        String email = authentication.getName();
        String usernameToAdd = body.getUsernameToAdd();
        String name = body.getName();
        ChatRoomType roomType = parseRoomType(body.getRoomType());
        if (roomType == ChatRoomType.STANDARD || roomType == ChatRoomType.RANDOM) {
            return ResponseEntity.badRequest().body(Map.of("error", roomType + " rooms are system-managed"));
        }

        User creator = userService.getUserEntityByEmail(email);
        if (usernameToAdd != null && !usernameToAdd.isBlank() && creator.getUsername().equalsIgnoreCase(usernameToAdd)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot invite yourself"));
        }
        if (usernameToAdd != null && !usernameToAdd.isBlank()) {
            User target = userService.getUserEntityByUsername(usernameToAdd);
            chatRoomService.enforcePremiumRoomAccess(creator, roomType);
            chatRoomService.enforcePremiumRoomAccess(target, roomType);
            if (roomType == ChatRoomType.DIRECT) {
                ChatInvitationActionResponseDTO result = chatInvitationService
                        .requestPrivateConversation(email, usernameToAdd);
                Map<String, Object> response = new LinkedHashMap<>();
                if (result.getChatRoom() != null) response.put("chatRoom", result.getChatRoom());
                if (result.getInvitation() != null) response.put("invite", result.getInvitation());
                response.put("chatRoomId", result.getChatRoomId());
                response.put("status", result.getStatus());
                response.put("message", result.getMessage());
                return ResponseEntity.status(
                                ChatInvitationStatus.ACCEPTED.name().equals(result.getStatus())
                                        ? HttpStatus.OK : HttpStatus.CREATED)
                        .body(response);
            }
        }

        freePlanLimitService.enforceConversationCreateLimit(creator);
        ChatRoom created = chatRoomService.createGroupRoom(email, null, name, roomType);
        ChatRoomSummaryDTO chatRoomSummary = chatRoomService.toSummaryDto(created);

        if (usernameToAdd != null && !usernameToAdd.isBlank()) {
            ChatInvitationActionResponseDTO inviteResult = chatInvitationService.createInvite(created.getId(), email, usernameToAdd);
            // A direct room is not usable or visible until the invite is accepted.
            chatRoomService.deactivateMembership(created.getId(), creator.getId());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("chatRoom", chatRoomSummary);
            response.put("invite", inviteResult.getInvitation());
            response.put("message", inviteResult.getMessage());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(chatRoomSummary);
    }

    // ---------------- Get All ChatRooms ----------------
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAll(Authentication authentication,
                                    @RequestParam(required = false) String scope) {
        try {
            User currentUser = userService.getUserEntityByEmail(authentication.getName());
            List<ChatRoomSummaryDTO> response = chatRoomService.getVisibleRooms(currentUser, scope);
            log.info("chatrooms.list userId={} count={}", currentUser.getId(), response.size());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Failed to load chat rooms for principal {}",
                    authentication != null ? authentication.getName() : "unknown", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to load chat rooms"));
        }
    }

    @PostMapping("/random-join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> randomJoin(@RequestBody @Valid RandomChatJoinRequestDTO request,
                                        Authentication authentication) {
        try {
            User currentUser = userService.getUserEntityByEmail(authentication.getName());
            return ResponseEntity.ok(matchmakingService.join(currentUser));
        } catch (PremiumRoomRequiredException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "code", "PREMIUM_ROOM_REQUIRED",
                            "error", "This room is available for PRO members only."
                    ));
        }
    }

    @PostMapping("/random-next")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> randomNext(@RequestBody(required = false) Map<String, Long> body,
                                        Authentication authentication) {
        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        Long currentRoomId = body == null ? null : body.get("currentRoomId");
        return ResponseEntity.ok(matchmakingService.next(currentUser, currentRoomId));
    }

    @PostMapping("/random-heartbeat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> randomHeartbeat(@RequestBody Map<String, Long> body,
                                                Authentication authentication) {
        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        matchmakingService.heartbeat(currentUser.getId(), body.get("roomId"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/random-waiting")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelRandomWaiting(Authentication authentication) {
        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        matchmakingService.cancel(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/online-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Long>> onlineCount() {
        return ResponseEntity.ok(Map.of("online", chatRoomService.getOnlineCount()));
    }

    // ---------------- Get Messages by ChatRoom ----------------
    @GetMapping("/{chatRoomId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMessagesByChatRoom(@PathVariable Long chatRoomId,
                                                   Authentication authentication) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("ChatRoom not found with id: " + chatRoomId));

        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isParticipant = chatRoomService.isActiveParticipant(chatRoomId, currentUser.getId());

        if (!isAdmin && !isParticipant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied to this chat room messages"));
        }

        List<MessageResponseDTO> messages = messageRepository.findByChatRoomId(chatRoomId).stream()
                .map(messageMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(messages);
    }

    // ---------------- Add Participant to ChatRoom ----------------
    @PostMapping("/{chatRoomId}/participants")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addParticipant(@PathVariable Long chatRoomId,
                                            @RequestBody @Valid ChatRoomParticipantInviteRequestDTO request,
                                            Authentication authentication) {
        ChatInvitationActionResponseDTO inviteResponse = chatInvitationService
                .createInvite(chatRoomId, authentication.getName(), request.getUsernameToAdd());
        return ResponseEntity.ok(inviteResponse);
    }

    // ---------------- Create Invite (new flow) ----------------
    @PostMapping("/{chatRoomId}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatInvitationActionResponseDTO> createInvite(@PathVariable Long chatRoomId,
                                                                        @RequestBody @Valid ChatRoomParticipantInviteRequestDTO request,
                                                                        Authentication authentication) {
        ChatInvitationActionResponseDTO response = chatInvitationService
                .createInvite(chatRoomId, authentication.getName(), request.getUsernameToAdd());
        return ResponseEntity.ok(response);
    }

    // ---------------- Delete ChatRoom ----------------
    @DeleteMapping("/{chatRoomId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteChatRoom(@PathVariable Long chatRoomId, Authentication authentication) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("ChatRoom not found"));

        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isCreator = chatRoom.getCreator() != null && chatRoom.getCreator().equals(currentUser);

        if (!isAdmin && !isCreator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only admin or creator can delete this chat"));
        }

        chatRoomRepository.delete(chatRoom);
        return ResponseEntity.ok(Map.of("message", "ChatRoom deleted"));
    }

    // ---------------- Leave ChatRoom (Delete for me) ----------------
    @DeleteMapping("/{chatRoomId}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> leaveChatRoom(@PathVariable String chatRoomId, Authentication authentication) {
        try {
            Long parsedRoomId = Long.parseLong(chatRoomId);
            Long authUserId = userService.getUserIdByEmail(authentication.getName());
            if (authUserId == null) {
                throw new AccessDeniedException("Only participants can leave this chat");
            }
            if (!matchmakingService.leaveConversation(parsedRoomId, authUserId)) {
                chatRoomService.leaveRoom(parsedRoomId, authUserId);
            }
            return ResponseEntity.noContent().build();
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid roomId"));
        } catch (ResourceNotFoundException.NotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only participants can leave this chat"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to leave chatRoomId={} principal={}",
                    chatRoomId,
                    authentication != null ? authentication.getName() : "unknown",
                    ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error occurred"));
        }
    }

    @DeleteMapping("/{chatRoomId}/my-chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> hideMyChat(@PathVariable String chatRoomId, Authentication authentication) {
        try {
            Long parsedRoomId = Long.parseLong(chatRoomId);
            Long authUserId = userService.getUserIdByEmail(authentication.getName());
            if (authUserId == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Chat room membership not found"));
            }
            chatRoomService.hideMyChat(parsedRoomId, authUserId);
            return ResponseEntity.noContent().build();
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid roomId"));
        } catch (ResourceNotFoundException.NotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Failed to hide chatRoomId={} principal={}",
                    chatRoomId,
                    authentication != null ? authentication.getName() : "unknown",
                    ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error occurred"));
        }
    }

    // ---------------- Admin Cleanup ----------------
    @PostMapping("/admin/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> cleanupRooms() {
        return ResponseEntity.ok(chatRoomService.cleanupInvalidAndDuplicatePrivateRooms());
    }

    private ChatRoomType parseRoomType(String rawRoomType) {
        if (rawRoomType == null || rawRoomType.isBlank()) {
            return ChatRoomType.DIRECT;
        }
        try {
            return ChatRoomType.valueOf(rawRoomType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid roomType");
        }
    }
}
