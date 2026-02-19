package com.confessionverse.backend.service;

import com.confessionverse.backend.model.ProcessedStripeEvent;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ProcessedStripeEventRepository;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeBillingWebhookServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Mock
    private BillingEmailService billingEmailService;

    private StripeBillingService stripeBillingService;
    private HttpServer httpServer;
    private AtomicReference<String> subscriptionResponse;
    private User user;
    private Subscription existingSubscription;

    @BeforeEach
    void setUp() throws Exception {
        stripeBillingService = new StripeBillingService(
                userRepository,
                subscriptionRepository,
                processedStripeEventRepository,
                billingEmailService,
                new ObjectMapper()
        );

        ReflectionTestUtils.setField(stripeBillingService, "stripeSecretKey", "sk_test");
        ReflectionTestUtils.setField(stripeBillingService, "webhookSigningSecret", "whsec_test");

        user = new User();
        user.setId(11L);
        user.setEmail("user@example.com");
        user.setUsername("user");
        user.setPremium(false);

        existingSubscription = new Subscription();
        existingSubscription.setUser(user);
        existingSubscription.setSubscriber(user);
        existingSubscription.setStripeCustomerId("cus_123");
        existingSubscription.setPlanType("FREE");
        existingSubscription.setStatus("checkout_pending");

        lenient().when(subscriptionRepository.findTopByStripeCustomerIdOrderByUpdatedAtDesc("cus_123"))
                .thenReturn(Optional.of(existingSubscription));
        lenient().when(subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(11L))
                .thenReturn(Optional.of(existingSubscription));
        lenient().when(subscriptionRepository.findByStripeSubscriptionId("sub_123"))
                .thenReturn(Optional.of(existingSubscription));
        lenient().when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionResponse = new AtomicReference<>(
                "{\"id\":\"sub_123\",\"customer\":\"cus_123\",\"status\":\"active\",\"current_period_start\":1735689600,\"current_period_end\":1738368000,\"cancel_at_period_end\":false,\"latest_invoice\":\"in_latest\"}"
        );

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/subscriptions/sub_123", exchange -> respond(exchange, subscriptionResponse.get()));
        httpServer.start();

        ReflectionTestUtils.setField(stripeBillingService, "stripeApiBase", "http://localhost:" + httpServer.getAddress().getPort() + "/v1");
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void checkoutSessionCompletedShouldUpgradeToPro() {
        String payload = "{\"id\":\"evt_checkout\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test\",\"customer\":\"cus_123\",\"subscription\":\"sub_123\"}}}";
        when(processedStripeEventRepository.existsByEventId("evt_checkout")).thenReturn(false);

        stripeBillingService.handleWebhook(payload, sign(payload));

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription saved = subscriptionCaptor.getValue();
        assertEquals("PRO", saved.getPlanType());
        assertEquals("active", saved.getStatus());
        assertEquals("sub_123", saved.getStripeSubscriptionId());
        assertTrue(Boolean.TRUE.equals(user.getPremium()));
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
    }

    @Test
    void invoicePaidShouldSetLastPaymentAtAndKeepPro() {
        String payload = "{\"id\":\"evt_invoice_paid\",\"type\":\"invoice.paid\",\"data\":{\"object\":{\"id\":\"in_1\",\"customer\":\"cus_123\",\"subscription\":\"sub_123\",\"amount_paid\":159,\"currency\":\"usd\",\"hosted_invoice_url\":\"https://invoice\",\"status_transitions\":{\"paid_at\":1735689700}}}}";
        when(processedStripeEventRepository.existsByEventId("evt_invoice_paid")).thenReturn(false);

        stripeBillingService.handleWebhook(payload, sign(payload));

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription saved = subscriptionCaptor.getValue();
        assertEquals("PRO", saved.getPlanType());
        assertNotNull(saved.getLastPaymentAt());
        assertEquals("in_1", saved.getLastInvoiceId());
        assertTrue(Boolean.TRUE.equals(user.getPremium()));
    }

    @Test
    void subscriptionDeletedShouldDowngradeToFree() {
        String payload = "{\"id\":\"evt_sub_deleted\",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_123\",\"customer\":\"cus_123\",\"status\":\"canceled\",\"current_period_start\":1735689600,\"current_period_end\":1738368000,\"cancel_at_period_end\":true}}}";
        when(processedStripeEventRepository.existsByEventId("evt_sub_deleted")).thenReturn(false);

        stripeBillingService.handleWebhook(payload, sign(payload));

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription saved = subscriptionCaptor.getValue();
        assertEquals("FREE", saved.getPlanType());
        assertEquals("canceled", saved.getStatus());
        assertFalse(Boolean.TRUE.equals(user.getPremium()));
    }

    @Test
    void duplicateEventIdShouldBeIgnored() {
        String payload = "{\"id\":\"evt_dup\",\"type\":\"invoice.paid\",\"data\":{\"object\":{\"id\":\"in_1\",\"customer\":\"cus_123\",\"subscription\":\"sub_123\"}}}";
        when(processedStripeEventRepository.existsByEventId("evt_dup")).thenReturn(true);

        stripeBillingService.handleWebhook(payload, sign(payload));

        verify(subscriptionRepository, never()).save(any());
        verify(processedStripeEventRepository, never()).save(any());
    }

    private String sign(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        String digest = hmacSha256Hex("whsec_test", signedPayload);
        return "t=" + timestamp + ",v1=" + digest;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
