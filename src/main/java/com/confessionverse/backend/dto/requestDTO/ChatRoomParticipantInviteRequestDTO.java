package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRoomParticipantInviteRequestDTO {
    @NotBlank(message = "usernameToAdd is required")
    private String usernameToAdd;
}
