package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RandomChatJoinRequestDTO {
    @NotBlank
    private String roomType;
}
