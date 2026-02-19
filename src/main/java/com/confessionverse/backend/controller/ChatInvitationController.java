package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.responseDTO.ChatInvitationActionResponseDTO;
import com.confessionverse.backend.dto.responseDTO.ChatInvitationDTO;
import com.confessionverse.backend.service.ChatInvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-invites")
@CrossOrigin(origins = "*")
public class ChatInvitationController {

    private final ChatInvitationService chatInvitationService;

    public ChatInvitationController(ChatInvitationService chatInvitationService) {
        this.chatInvitationService = chatInvitationService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatInvitationDTO>> myPendingInvites(Authentication authentication) {
        List<ChatInvitationDTO> invites = chatInvitationService.getPendingInvitesForCurrentUser(authentication.getName());
        return ResponseEntity.ok(invites);
    }

    @PostMapping("/{inviteId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatInvitationActionResponseDTO> accept(@PathVariable Long inviteId,
                                                                  Authentication authentication) {
        ChatInvitationActionResponseDTO response = chatInvitationService.acceptInvitation(inviteId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{inviteId}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatInvitationActionResponseDTO> decline(@PathVariable Long inviteId,
                                                                   Authentication authentication) {
        ChatInvitationActionResponseDTO response = chatInvitationService.declineInvitation(inviteId, authentication.getName());
        return ResponseEntity.ok(response);
    }
}
