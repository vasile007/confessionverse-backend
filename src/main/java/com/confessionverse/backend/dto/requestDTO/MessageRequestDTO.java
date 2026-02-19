package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

@Data
public class MessageRequestDTO {
    private Long chatRoomId;
    private Long senderId;
    private String content;
}

