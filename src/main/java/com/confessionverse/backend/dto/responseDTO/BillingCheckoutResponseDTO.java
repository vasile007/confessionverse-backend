package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BillingCheckoutResponseDTO {
    private String checkoutUrl;
    private String customerPortalUrl;
}
