package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageResponseDTO {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private String content;
    private LocalDateTime timestamp;
    private UserSummaryDTO sender;
}

