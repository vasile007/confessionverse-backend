package com.confessionverse.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class BoostDTO {
    private Long id;
    private Long confessionId;
    private Long userId;
    private String boostType;
    private LocalDateTime date;
}
