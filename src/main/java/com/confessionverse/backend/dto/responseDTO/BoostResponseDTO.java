package com.confessionverse.backend.dto.responseDTO;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BoostResponseDTO {
    private Long id;
    private String boostType;
    private String username;
    private Long confessionId;
    private LocalDateTime date;
}

