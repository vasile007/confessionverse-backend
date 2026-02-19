package com.confessionverse.backend.dto.requestDTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SubscriptionRequestDTO {
    private Long userId;
    private String planType;    // ex: "BASIC", "PREMIUM"
    private LocalDate startDate;
    private LocalDate endDate;
}

