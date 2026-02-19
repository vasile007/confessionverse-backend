
package com.confessionverse.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class MessageDTO {
    private Long id;
    private String content;
    private LocalDateTime timestamp;
    private Long chatRoomId;
    private Long senderId;
}
