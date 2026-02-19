package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class ChatRoomResponseDTO {
    private Long id;
    private LocalDateTime createdAt;
    private Set<Long> participantIds;
    private Set<Long> messageIds;
}

