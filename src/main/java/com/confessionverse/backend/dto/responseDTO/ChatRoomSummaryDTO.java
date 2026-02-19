package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomSummaryDTO {
    private Long id;
    private String name;
    private String username;
    private String roomType;
    private Boolean premium;
    private LocalDateTime createdAt;
    private ChatParticipantDTO creator;
    private List<ChatParticipantDTO> participants;
}
