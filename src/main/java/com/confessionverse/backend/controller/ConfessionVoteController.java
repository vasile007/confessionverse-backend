package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.VoteRequestDto;
import com.confessionverse.backend.dto.responseDTO.ApiResponse;
import com.confessionverse.backend.dto.responseDTO.VoteResponseDto;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.service.ConfessionVoteService;
import com.confessionverse.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/votes")
public class ConfessionVoteController {

    private final ConfessionVoteService voteService;
    private final UserService userService;

    public ConfessionVoteController(ConfessionVoteService voteService, UserService userService) {
        this.voteService = voteService;
        this.userService = userService;
    }

    @PostMapping("/{confessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> voteOnConfession(
            @PathVariable Long confessionId,
            @Valid @RequestBody VoteRequestDto voteRequest,
            HttpServletRequest request,
            Authentication authentication) {

        String voterIp = extractClientIp(request);
        log.info("Received vote request for confessionId={} from IP={}", confessionId, voterIp);

        try {
            User voterUser = null;
            try {
                voterUser = userService.getUserEntityByEmail(authentication.getName());
            } catch (ResourceNotFoundException ex) {
                try {
                    voterUser = userService.getUserEntityByUsername(authentication.getName());
                } catch (ResourceNotFoundException ex2) {
                    log.warn("User not found for identifier={}", authentication.getName());
                }
                if (voterUser == null) {
                    return ResponseEntity.status(401)
                            .body(new ApiResponse(false, "Authenticated user not found"));
                }
            }
            VoteResponseDto responseDto = voteService.vote(confessionId, voterIp, voteRequest, voterUser);
            return ResponseEntity.ok(responseDto);
        } catch (IllegalArgumentException ex) {
            log.warn("Bad request: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Vote data integrity violation for confessionId={}: {}", confessionId, ex.getMostSpecificCause().getMessage());
            return ResponseEntity.status(409).body(new ApiResponse(false, "Vote could not be saved due to data constraints"));
        } catch (Exception ex) {
            log.error("Internal server error while voting", ex);
            return ResponseEntity.status(500).body(new ApiResponse(false, "Internal server error"));
        }
    }

    @GetMapping("/{confessionId}/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VoteResponseDto> getVotesSummary(@PathVariable Long confessionId) {
        try {
            VoteResponseDto summary = voteService.getVotesSummary(confessionId);
            return ResponseEntity.ok(summary);
        } catch (Exception ex) {
            log.error("Failed to get votes summary for confessionId={}", confessionId, ex);
            return ResponseEntity.ok(VoteResponseDto.builder()
                    .confessionId(confessionId)
                    .likeCount(0)
                    .dislikeCount(0)
                    .message("Summary unavailable")
                    .build());
        }
    }

    @GetMapping("/{confessionId}/user-vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VoteResponseDto> getUserVoteInfo(
            @PathVariable Long confessionId,
            Authentication authentication) {

        User voterUser;
        try {
            // încercăm email (JWT standard)
            voterUser = userService.getUserEntityByEmail(authentication.getName());
        } catch (ResourceNotFoundException ex) {
            try {
                // fallback username (dacă ai login pe username)
                voterUser = userService.getUserEntityByUsername(authentication.getName());
            } catch (ResourceNotFoundException ex2) {
                // 🚨 authenticated dar nu există user → problemă de securitate
                log.error("Authenticated principal '{}' not mapped to User", authentication.getName());
                return ResponseEntity.status(403).build();
            }
        }

        VoteResponseDto response =
                voteService.getUserVoteInfo(confessionId, voterUser);

        return ResponseEntity.ok(response);
    }


    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    @GetMapping("/{voteId}")
    @PreAuthorize("@ownershipUtil.checkOwnership(T(com.confessionverse.backend.model.Confession), @confessionVoteService.getConfessionIdByVoteId(#voteId), authentication.name)")
    public ResponseEntity<VoteResponseDto> getVoteById(@PathVariable Long voteId) {
        VoteResponseDto response = voteService.getVoteById(voteId);
        return ResponseEntity.ok(response);
    }

}








