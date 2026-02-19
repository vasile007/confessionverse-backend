package com.confessionverse.backend.controller;

import com.confessionverse.backend.exception.BillingNotConfiguredException;
import com.confessionverse.backend.exception.GlobalExceptionHandler;
import com.confessionverse.backend.service.StripeBillingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingControllerTest {

    private StripeBillingService stripeBillingService;
    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        stripeBillingService = Mockito.mock(StripeBillingService.class);
        BillingController billingController = new BillingController(stripeBillingService, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(billingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authentication = new UsernamePasswordAuthenticationToken("user@example.com", "n/a");
    }

    @Test
    void shouldReturnBadRequestForUnsupportedCurrency() throws Exception {
        when(stripeBillingService.createCheckoutSessionForUser("user@example.com", "eur"))
                .thenThrow(new IllegalArgumentException("Unsupported currency: eur. Supported currencies: usd, gbp, ron"));

        mockMvc.perform(post("/api/billing/checkout-session")
                .param("currency", "eur")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenBillingConfigMissing() throws Exception {
        when(stripeBillingService.createCheckoutSessionForUser("user@example.com", "gbp"))
                .thenThrow(new BillingNotConfiguredException("Stripe is not configured"));

        mockMvc.perform(post("/api/billing/checkout-session")
                .param("currency", "gbp")
                        .principal(authentication))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"));
    }

    @Test
    void shouldReturnBadRequestForInvalidWebhookSignature() throws Exception {
        String payload = "{\"id\":\"evt_1\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}";
        doThrow(new IllegalArgumentException("Invalid Stripe signature"))
                .when(stripeBillingService).handleWebhook(payload, "t=123,v1=bad");

        mockMvc.perform(post("/api/billing/webhook")
                        .header("Stripe-Signature", "t=123,v1=bad")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("Invalid Stripe signature"));
    }
}
