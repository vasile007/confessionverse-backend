package com.confessionverse.backend.service;

import com.confessionverse.backend.exception.BillingNotConfiguredException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeBillingServiceTest {

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
    private AtomicReference<String> checkoutSessionBody;

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
        ReflectionTestUtils.setField(stripeBillingService, "checkoutSuccessUrl", "http://localhost:5173/billing/success");
        ReflectionTestUtils.setField(stripeBillingService, "checkoutCancelUrl", "http://localhost:5173/billing/cancel");
        ReflectionTestUtils.setField(stripeBillingService, "portalReturnUrl", "http://localhost:5173/settings/billing");
        ReflectionTestUtils.setField(stripeBillingService, "checkoutProductName", "ConfessionVerse Pro");
        ReflectionTestUtils.setField(stripeBillingService, "checkoutDefaultCurrency", "usd");
        ReflectionTestUtils.setField(stripeBillingService, "checkoutUnitAmountCentsUsd", 159L);
        ReflectionTestUtils.setField(stripeBillingService, "checkoutUnitAmountCentsGbp", 129L);
        ReflectionTestUtils.setField(stripeBillingService, "checkoutUnitAmountCentsRon", 790L);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setUsername("user");

        lenient().when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        lenient().when(subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        lenient().when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        checkoutSessionBody = new AtomicReference<>();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/customers", exchange -> respond(exchange, "{\"id\":\"cus_123\"}"));
        httpServer.createContext("/v1/checkout/sessions", exchange -> {
            checkoutSessionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"url\":\"https://checkout.test/session\"}");
        });
        httpServer.createContext("/v1/billing_portal/sessions", exchange -> respond(exchange, "{\"url\":\"https://billing.test/portal\"}"));
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
    void shouldUseDefaultCurrencyWhenMissing() {
        stripeBillingService.createCheckoutSessionForUser("user@example.com", null);

        String body = checkoutSessionBody.get();
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=usd"));
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=159"));
    }

    @Test
    void shouldSendUsdAmount() {
        stripeBillingService.createCheckoutSessionForUser("user@example.com", "usd");

        String body = checkoutSessionBody.get();
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=usd"));
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=159"));
    }

    @Test
    void shouldSendGbpAmount() {
        stripeBillingService.createCheckoutSessionForUser("user@example.com", "gbp");

        String body = checkoutSessionBody.get();
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=gbp"));
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=129"));
    }

    @Test
    void shouldSendRonAmount() {
        stripeBillingService.createCheckoutSessionForUser("user@example.com", "ron");

        String body = checkoutSessionBody.get();
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bcurrency%5D=ron"));
        assertTrue(body.contains("line_items%5B0%5D%5Bprice_data%5D%5Bunit_amount%5D=790"));
    }

    @Test
    void shouldFailWhenSelectedCurrencyAmountMissing() {
        ReflectionTestUtils.setField(stripeBillingService, "checkoutUnitAmountCentsGbp", 0L);

        assertThrows(BillingNotConfiguredException.class,
                () -> stripeBillingService.createCheckoutSessionForUser("user@example.com", "gbp"));
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
