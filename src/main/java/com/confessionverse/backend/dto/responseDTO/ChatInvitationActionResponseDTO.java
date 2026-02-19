package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatInvitationActionResponseDTO {
    private String message;
    private Long inviteId;
    private String status;
    private Long chatRoomId;
    private ChatInvitationDTO invitation;
    private ChatRoomSummaryDTO chatRoom;
}
