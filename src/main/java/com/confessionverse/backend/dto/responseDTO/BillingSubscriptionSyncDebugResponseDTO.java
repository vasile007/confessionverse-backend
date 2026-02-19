package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillingSubscriptionSyncDebugResponseDTO {
    private Long userId;
    private String email;
    private boolean premium;
    private LatestSubscription latestSubscription;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LatestSubscription {
        private Long id;
        private String planType;
        private String status;
        private String stripeCustomerId;
        private String stripeSubscriptionId;
        private LocalDateTime currentPeriodEnd;
        private LocalDateTime lastPaymentAt;
    }
}
