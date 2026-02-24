package com.confessionverse.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    @NotBlank(message = "Sender is required")
    private String sender;

    @NotBlank(message = "Content must not be blank")
    private String content;

    @NotBlank(message = "Chat room ID is required")
    private String chatRoomId;

    private String timestamp; // e.g., ISO-8601 string generated in backend or frontend

    private String receiver; // for private messages, optional
}

