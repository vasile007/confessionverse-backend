package com.confessionverse.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
@Data
public class ConfessionDTO {
    private Long id;
    private String content;
    private Long userId;
    private LocalDateTime createdAt;
    private Set<Long> boostIds;
}

