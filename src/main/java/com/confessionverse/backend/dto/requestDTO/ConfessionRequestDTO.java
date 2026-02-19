package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

@Data
public class ConfessionRequestDTO {
    private String content;
    private Long userId;
}
