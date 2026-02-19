package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

import java.util.Set;

@Data
public class ChatRoomRequestDTO {
    private Set<Long> participantIds;  // user IDs participating in chatroom
}

