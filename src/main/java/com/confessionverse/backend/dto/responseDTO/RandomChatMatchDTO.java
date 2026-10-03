package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RandomChatMatchDTO {
    private String status;
    private String roomType;
    private ChatRoomSummaryDTO chatRoom;
}
