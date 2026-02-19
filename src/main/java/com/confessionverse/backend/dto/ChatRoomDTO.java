package com.confessionverse.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
@Data
public class ChatRoomDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String roomType;
    private Set<Long> participantIds;
    private Set<Long> messageIds;
}

