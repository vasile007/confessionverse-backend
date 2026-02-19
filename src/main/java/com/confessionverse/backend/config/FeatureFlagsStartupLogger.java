package com.confessionverse.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Arrays;

@Component
public class FeatureFlagsStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagsStartupLogger.class);

    private final Environment environment;
    private final PremiumGatesProperties premiumGatesProperties;
    private final LimitsProperties limitsProperties;
    private final ChatInvitationProperties chatInvitationProperties;

    public FeatureFlagsStartupLogger(Environment environment,
                                     PremiumGatesProperties premiumGatesProperties,
                                     LimitsProperties limitsProperties,
                                     ChatInvitationProperties chatInvitationProperties) {
        this.environment = environment;
        this.premiumGatesProperties = premiumGatesProperties;
        this.limitsProperties = limitsProperties;
        this.chatInvitationProperties = chatInvitationProperties;
    }

    @PostConstruct
    public void logFeatureFlags() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length == 0 ? "default" : String.join(",", Arrays.asList(activeProfiles));
        log.info("[BillingConfig] profile={} premium.gates.enabled={} limits.enabled={} chat.invites.ttl-hours={}",
                profile,
                premiumGatesProperties.isEnabled(),
                limitsProperties.isEnabled(),
                chatInvitationProperties.getTtlHours());
    }
}
