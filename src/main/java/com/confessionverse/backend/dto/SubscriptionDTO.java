package com.confessionverse.backend.dto;

import lombok.Data;

import java.time.LocalDate;
@Data
public class SubscriptionDTO {
    private Long id;
    private String planType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long userId;
}

