package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SubscriptionResponseDTO {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String planType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime lastPaymentAt;
    private String lastInvoiceId;
    private String stripeCustomerIdShort;
    private String stripeSubscriptionIdShort;
}


