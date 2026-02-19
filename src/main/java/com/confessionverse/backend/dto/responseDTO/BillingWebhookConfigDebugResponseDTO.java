package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BillingWebhookConfigDebugResponseDTO {
    private boolean stripeSecretConfigured;
    private boolean webhookSigningSecretConfigured;
    private String serverTime;
}
