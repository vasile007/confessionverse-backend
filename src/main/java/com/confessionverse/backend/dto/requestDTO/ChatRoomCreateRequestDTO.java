package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

@Data
public class ChatRoomCreateRequestDTO {
    private String usernameToAdd;
    private String roomType;
    private String name;
}
