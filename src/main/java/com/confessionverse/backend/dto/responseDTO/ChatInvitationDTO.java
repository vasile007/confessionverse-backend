package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatInvitationDTO {
    private Long id;
    private Long chatRoomId;
    private String inviterUsername;
    private String inviteeUsername;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
