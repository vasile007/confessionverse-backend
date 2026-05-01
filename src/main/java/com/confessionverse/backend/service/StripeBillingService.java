package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.responseDTO.BillingCheckoutResponseDTO;
import com.confessionverse.backend.dto.responseDTO.BillingSubscriptionSyncDebugResponseDTO;
import com.confessionverse.backend.dto.responseDTO.BillingWebhookConfigDebugResponseDTO;
import com.confessionverse.backend.exception.BillingNotConfiguredException;
import com.confessionverse.backend.model.ProcessedStripeEvent;
import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ProcessedStripeEventRepository;
import com.confessionverse.backend.repository.SubscriptionRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

@Service
public class StripeBillingService {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingService.class);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final BillingEmailService billingEmailService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-signing-secret}")
    private String webhookSigningSecret;

    @Value("${stripe.checkout.default-currency:usd}")
    private String checkoutDefaultCurrency;

    @Value("${stripe.checkout.unit-amount-cents.usd:0}")
    private Long checkoutUnitAmountCentsUsd;

    @Value("${stripe.checkout.unit-amount-cents.gbp:0}")
    private Long checkoutUnitAmountCentsGbp;

    @Value("${stripe.checkout.unit-amount-cents.ron:0}")
    private Long checkoutUnitAmountCentsRon;

    @Value("${stripe.checkout.product-name:ConfessionVerse Pro}")
    private String checkoutProductName;

    @Value("${stripe.api-base:https://api.stripe.com/v1}")
    private String stripeApiBase;

    @Value("${app.billing.success-url}")
    private String checkoutSuccessUrl;

    @Value("${app.billing.cancel-url}")
    private String checkoutCancelUrl;

    @Value("${app.billing.portal-return-url}")
    private String portalReturnUrl;

    public StripeBillingService(UserRepository userRepository,
                                SubscriptionRepository subscriptionRepository,
                                ProcessedStripeEventRepository processedStripeEventRepository,
                                BillingEmailService billingEmailService,
                                ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.billingEmailService = billingEmailService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @PostConstruct
    void logWebhookConfigurationStatus() {
        log.info("Stripe webhook signing secret configured: {}", !isBlank(webhookSigningSecret));
    }

    @Transactional
    public BillingCheckoutResponseDTO createCheckoutSessionForUser(String userEmail, String requestedCurrency) {
        String selectedCurrency = resolveCurrency(requestedCurrency);
        long unitAmount = requireConfiguredAmount(selectedCurrency);
        ensureCheckoutConfigured();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String customerId = ensureStripeCustomer(user);
        String checkoutUrl = createCheckoutSession(customerId, user.getId(), selectedCurrency, unitAmount);
        String customerPortalUrl = createCustomerPortalSession(customerId);

        ensureSubscriptionRecordForCustomer(user, customerId);
        return new BillingCheckoutResponseDTO(checkoutUrl, customerPortalUrl);
    }

    @Transactional
    public void handleWebhook(String payload, String stripeSignature) {
        log.info("handleWebhook start");
        ensureWebhookConfigured();
        if (!isValidStripeSignature(payload, stripeSignature)) {
            log.warn("Stripe webhook rejected: signature verification failed");
            throw new IllegalArgumentException("Invalid Stripe signature");
        }

        JsonNode event = readJson(payload);
        String eventId = textValue(event, "id");
        String eventType = textValue(event, "type");
        log.info("Webhook event parsed id={} type={}", eventId, eventType);
        if (eventId == null || eventType == null) {
            return;
        }

        if (processedStripeEventRepository.existsByEventId(eventId)) {
            log.info("event skipped duplicate eventId={}", eventId);
            return;
        }

        try {
            JsonNode objectNode = event.path("data").path("object");
            Long resolvedUserId = null;
            log.info("Webhook branch selected type={}", eventType);
            switch (eventType) {
                case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                        resolvedUserId = handleSubscriptionObject(objectNode);
                case "invoice.paid" -> resolvedUserId = handleInvoicePaid(objectNode);
                case "invoice.payment_failed" -> resolvedUserId = handleInvoicePaymentFailed(objectNode);
                case "checkout.session.completed" -> resolvedUserId = handleCheckoutSessionCompleted(objectNode);
                default -> {
                }
            }
            log.info("event processed successfully id={} type={}", eventId, eventType);

            ProcessedStripeEvent processedEvent = new ProcessedStripeEvent();
            processedEvent.setEventId(eventId);
            processedEvent.setEventType(eventType);
            processedStripeEventRepository.save(processedEvent);
            log.info("Processed Stripe webhook event id={} type={} userId={}", eventId, eventType, resolvedUserId);
        } catch (DataIntegrityViolationException ex) {
            log.error("Stripe webhook persistence exception for event id={} type={}", eventId, eventType, ex);
        } catch (Exception ex) {
            log.error("Stripe webhook failed for event id={} type={}", eventId, eventType, ex);
            throw ex;
        }
    }

    @Transactional
    public BillingSubscriptionSyncDebugResponseDTO getSubscriptionSyncDebugForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Subscription> subscriptionOptional = subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(user.getId());
        Subscription subscription = subscriptionOptional.orElse(null);
        BillingSubscriptionSyncDebugResponseDTO.LatestSubscription latestSubscription = subscription == null ? null
                : new BillingSubscriptionSyncDebugResponseDTO.LatestSubscription(
                subscription.getId(),
                subscription.getPlanType(),
                subscription.getStatus(),
                subscription.getStripeCustomerId(),
                subscription.getStripeSubscriptionId(),
                subscription.getCurrentPeriodEnd(),
                subscription.getLastPaymentAt()
        );
        return new BillingSubscriptionSyncDebugResponseDTO(
                user.getId(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getPremium()),
                latestSubscription
        );
    }

    public BillingWebhookConfigDebugResponseDTO getWebhookConfigDebug() {
        return new BillingWebhookConfigDebugResponseDTO(
                !isBlank(stripeSecretKey),
                !isBlank(webhookSigningSecret),
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
    }

    private Long handleSubscriptionObject(JsonNode subscriptionNode) {
        String customerId = textValue(subscriptionNode, "customer");
        if (customerId == null) {
            return null;
        }

        Optional<User> userOptional = resolveUserByStripeCustomerId(customerId);
        if (userOptional.isEmpty()) {
            return null;
        }

        String previousStatus = subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(userOptional.get().getId())
                .map(Subscription::getStatus)
                .orElse(null);
        Subscription subscription = upsertSubscription(userOptional.get(), subscriptionNode, null, null);
        sendSubscriptionLifecycleEmail(userOptional.get(), subscription, previousStatus);
        return userOptional.get().getId();
    }

    private Long handleInvoicePaid(JsonNode invoiceNode) {
        String customerId = textValue(invoiceNode, "customer");
        String subscriptionId = textValue(invoiceNode, "subscription");
        if (customerId == null || subscriptionId == null) {
            return null;
        }

        Optional<User> userOptional = resolveUserByStripeCustomerId(customerId);
        if (userOptional.isEmpty()) {
            return null;
        }

        JsonNode subscriptionNode = retrieveStripeSubscription(subscriptionId);
        LocalDateTime paidAt = toLocalDateTime(longValue(invoiceNode.path("status_transitions"), "paid_at"));
        String invoiceId = textValue(invoiceNode, "id");

        Subscription subscription = upsertSubscription(userOptional.get(), subscriptionNode, invoiceId, paidAt);
        billingEmailService.sendInvoicePaidConfirmation(
                userOptional.get().getEmail(),
                subscription.getPlanType(),
                longValue(invoiceNode, "amount_paid"),
                textValue(invoiceNode, "currency"),
                paidAt,
                invoiceId,
                textValue(invoiceNode, "hosted_invoice_url")
        );
        return userOptional.get().getId();
    }

    private Long handleInvoicePaymentFailed(JsonNode invoiceNode) {
        String customerId = textValue(invoiceNode, "customer");
        String subscriptionId = textValue(invoiceNode, "subscription");
        if (customerId == null || subscriptionId == null) {
            return null;
        }

        Optional<User> userOptional = resolveUserByStripeCustomerId(customerId);
        if (userOptional.isEmpty()) {
            return null;
        }

        JsonNode subscriptionNode = retrieveStripeSubscription(subscriptionId);
        String invoiceId = textValue(invoiceNode, "id");
        Subscription subscription = upsertSubscription(userOptional.get(), subscriptionNode, invoiceId, null);
        billingEmailService.sendInvoicePaymentFailedWarning(
                userOptional.get().getEmail(),
                subscription.getPlanType(),
                invoiceId,
                textValue(invoiceNode, "hosted_invoice_url")
        );
        return userOptional.get().getId();
    }

    private Long handleCheckoutSessionCompleted(JsonNode sessionNode) {
        String customerId = textValue(sessionNode, "customer");
        String subscriptionId = textValue(sessionNode, "subscription");
        if (customerId == null || subscriptionId == null) {
            return null;
        }

        Optional<User> userOptional = resolveUserByStripeCustomerId(customerId);
        if (userOptional.isEmpty()) {
            return null;
        }

        JsonNode subscriptionNode = retrieveStripeSubscription(subscriptionId);
        String invoiceId = textValue(sessionNode, "invoice");
        upsertSubscription(userOptional.get(), subscriptionNode, invoiceId, null);
        return userOptional.get().getId();
    }

    private Subscription upsertSubscription(User user,
                                            JsonNode subscriptionNode,
                                            String lastInvoiceId,
                                            LocalDateTime lastPaymentAt) {
        String stripeSubscriptionId = textValue(subscriptionNode, "id");
        Subscription subscription = findSubscriptionForUpsert(user.getId(), stripeSubscriptionId);

        subscription.setUser(user);
        subscription.setSubscriber(user);
        subscription.setStripeCustomerId(textValue(subscriptionNode, "customer"));
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(textValue(subscriptionNode, "status"));
        subscription.setCurrentPeriodStart(toLocalDateTime(longValue(subscriptionNode, "current_period_start")));
        subscription.setCurrentPeriodEnd(toLocalDateTime(longValue(subscriptionNode, "current_period_end")));
        subscription.setCancelAtPeriodEnd(booleanValue(subscriptionNode, "cancel_at_period_end"));
        if (lastPaymentAt != null) {
            subscription.setLastPaymentAt(lastPaymentAt);
        }
        String invoiceId = firstNonBlank(lastInvoiceId, textValue(subscriptionNode, "latest_invoice"));
        if (invoiceId != null) {
            subscription.setLastInvoiceId(invoiceId);
        }

        boolean pro = isProStatus(subscription.getStatus());
        subscription.setPlanType(pro ? "PRO" : "FREE");
        subscription.setStartDate(subscription.getCurrentPeriodStart() == null ? null : subscription.getCurrentPeriodStart().toLocalDate());
        subscription.setEndDate(subscription.getCurrentPeriodEnd() == null ? null : subscription.getCurrentPeriodEnd().toLocalDate());
        user.setPremium(pro);
        userRepository.save(user);

        return subscriptionRepository.save(subscription);
    }

    private Subscription findSubscriptionForUpsert(Long userId, String stripeSubscriptionId) {
        if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()) {
            return subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId)
                    .or(() -> subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(userId))
                    .orElseGet(Subscription::new);
        }
        return subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(Subscription::new);
    }

    private Optional<User> resolveUserByStripeCustomerId(String stripeCustomerId) {
        Optional<Subscription> existingSubscription = subscriptionRepository
                .findTopByStripeCustomerIdOrderByUpdatedAtDesc(stripeCustomerId);
        if (existingSubscription.isPresent() && existingSubscription.get().getUser() != null) {
            return Optional.of(existingSubscription.get().getUser());
        }

        JsonNode customer = getStripeCustomer(stripeCustomerId);
        String userIdRaw = customer.path("metadata").path("app_user_id").asText(null);
        if (userIdRaw == null) {
            return Optional.empty();
        }
        try {
            Long userId = Long.parseLong(userIdRaw);
            return userRepository.findById(userId);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String ensureStripeCustomer(User user) {
        Optional<Subscription> existingSubscription = subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(user.getId());
        if (existingSubscription.isPresent()) {
            String existingCustomerId = existingSubscription.get().getStripeCustomerId();
            if (existingCustomerId != null && !existingCustomerId.isBlank()) {
                updateStripeCustomer(existingCustomerId, user);
                return existingCustomerId;
            }
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", user.getEmail());
        form.add("name", user.getUsername());
        form.add("metadata[app_user_id]", String.valueOf(user.getId()));

        String response = stripePost("/customers", form);
        return readJson(response).path("id").asText();
    }

    private void ensureCheckoutConfigured() {
        if (isBlank(stripeSecretKey) || isBlank(checkoutSuccessUrl) || isBlank(checkoutCancelUrl) || isBlank(checkoutProductName)) {
            throw new BillingNotConfiguredException("Stripe is not configured");
        }
    }

    private void ensureWebhookConfigured() {
        if (isBlank(stripeSecretKey) || isBlank(webhookSigningSecret)) {
            throw new BillingNotConfiguredException("Stripe is not configured");
        }
    }

    private void updateStripeCustomer(String customerId, User user) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", user.getEmail());
        form.add("name", user.getUsername());
        form.add("metadata[app_user_id]", String.valueOf(user.getId()));
        stripePost("/customers/" + customerId, form);
    }

    private String createCheckoutSession(String customerId, Long userId, String currency, long unitAmount) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "subscription");
        form.add("customer", customerId);
        form.add("success_url", checkoutSuccessUrl);
        form.add("cancel_url", checkoutCancelUrl);
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(unitAmount));
        form.add("line_items[0][price_data][product_data][name]", checkoutProductName);
        form.add("line_items[0][price_data][recurring][interval]", "month");
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[app_user_id]", String.valueOf(userId));

        String response = stripePost("/checkout/sessions", form);
        return readJson(response).path("url").asText();
    }

    private String createCustomerPortalSession(String customerId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("return_url", portalReturnUrl);

        String response = stripePost("/billing_portal/sessions", form);
        return readJson(response).path("url").asText();
    }

    private JsonNode retrieveStripeSubscription(String subscriptionId) {
        String response = restClient.get()
                .uri(stripeApiBase + "/subscriptions/" + subscriptionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + stripeSecretKey)
                .retrieve()
                .body(String.class);
        return readJson(response);
    }

    private JsonNode getStripeCustomer(String customerId) {
        String response = restClient.get()
                .uri(stripeApiBase + "/customers/" + customerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + stripeSecretKey)
                .retrieve()
                .body(String.class);
        return readJson(response);
    }

    private String stripePost(String path, MultiValueMap<String, String> formData) {
        return restClient.post()
                .uri(stripeApiBase + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + stripeSecretKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(String.class);
    }

    private boolean isValidStripeSignature(String payload, String stripeSignature) {
        if (stripeSignature == null || stripeSignature.isBlank() || webhookSigningSecret == null || webhookSigningSecret.isBlank()) {
            log.warn("Stripe signature verification failed: missing signature header or webhook secret configuration");
            return false;
        }

        String[] parts = stripeSignature.split(",");
        String timestamp = null;
        String expectedSignature = null;
        for (String part : parts) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("t".equals(keyValue[0])) {
                timestamp = keyValue[1];
            }
            if ("v1".equals(keyValue[0])) {
                expectedSignature = keyValue[1];
            }
        }
        log.info("Stripe signature parsed fields: timestampPresent={} v1Present={}",
                timestamp != null,
                expectedSignature != null);

        if (timestamp == null || expectedSignature == null) {
            log.warn("Stripe signature verification failed: missing t or v1 fields");
            return false;
        }

        boolean withinTolerance;
        try {
            long ts = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            withinTolerance = Math.abs(now - ts) <= 300;
            log.info("Stripe signature tolerance check passed={}", withinTolerance);
            if (!withinTolerance) {
                log.warn("Stripe signature verification failed: timestamp outside tolerance");
                return false;
            }
        } catch (NumberFormatException ex) {
            log.info("Stripe signature tolerance check passed=false");
            log.warn("Stripe signature verification failed: invalid timestamp");
            return false;
        }

        String signedPayload = timestamp + "." + payload;
        String calculatedSignature = hmacSha256Hex(webhookSigningSecret, signedPayload);
        boolean valid = MessageDigest.isEqual(
                calculatedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
        if (!valid) {
            log.warn("Stripe signature verification failed: digest mismatch");
        }
        log.info("Stripe signature valid={}", valid);
        return valid;
    }

    private String hmacSha256Hex(String secret, String payload) {
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
            throw new IllegalStateException("Could not verify Stripe signature");
        }
    }

    private void ensureSubscriptionRecordForCustomer(User user, String customerId) {
        Subscription subscription = subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(user.getId())
                .orElseGet(Subscription::new);
        subscription.setUser(user);
        subscription.setSubscriber(user);
        subscription.setPlanType(subscription.getPlanType() == null ? "FREE" : subscription.getPlanType());
        subscription.setStatus(subscription.getStatus() == null ? "checkout_pending" : subscription.getStatus());
        subscription.setStripeCustomerId(customerId);
        subscriptionRepository.save(subscription);
    }

    private boolean isProStatus(String status) {
        return "active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status);
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload");
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return null;
        }
        return node.path(fieldName).asLong();
    }

    private boolean booleanValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.path(fieldName).isMissingNode() || node.path(fieldName).isNull()) {
            return false;
        }
        return node.path(fieldName).asBoolean(false);
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveCurrency(String requestedCurrency) {
        String candidate = isBlank(requestedCurrency) ? checkoutDefaultCurrency : requestedCurrency;
        if (isBlank(candidate)) {
            throw new BillingNotConfiguredException("Stripe is not configured");
        }

        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (!isSupportedCurrency(normalized)) {
            if (isBlank(requestedCurrency)) {
                throw new BillingNotConfiguredException("Stripe is not configured");
            }
            throw new IllegalArgumentException("Unsupported currency: " + requestedCurrency + ". Supported currencies: usd, gbp, ron");
        }

        return normalized;
    }

    private long requireConfiguredAmount(String currency) {
        Long configuredAmount = switch (currency) {
            case "usd" -> checkoutUnitAmountCentsUsd;
            case "gbp" -> checkoutUnitAmountCentsGbp;
            case "ron" -> checkoutUnitAmountCentsRon;
            default -> null;
        };

        if (configuredAmount == null || configuredAmount <= 0) {
            throw new BillingNotConfiguredException("Stripe is not configured");
        }

        return configuredAmount;
    }

    private boolean isSupportedCurrency(String currency) {
        return "usd".equals(currency) || "gbp".equals(currency) || "ron".equals(currency);
    }

    private void sendSubscriptionLifecycleEmail(User user, Subscription subscription, String previousStatus) {
        String currentStatus = subscription.getStatus();
        if ("active".equalsIgnoreCase(currentStatus) && !isSameStatus(previousStatus, currentStatus)) {
            billingEmailService.sendSubscriptionActiveEmail(
                    user.getEmail(),
                    subscription.getPlanType(),
                    subscription.getCurrentPeriodEnd(),
                    portalReturnUrl
            );
            return;
        }

        if ("canceled".equalsIgnoreCase(currentStatus) && !isSameStatus(previousStatus, currentStatus)) {
            billingEmailService.sendSubscriptionCancelledEmail(
                    user.getEmail(),
                    subscription.getPlanType(),
                    subscription.getCurrentPeriodEnd(),
                    portalReturnUrl
            );
        }
    }

    private boolean isSameStatus(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equalsIgnoreCase(right);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
