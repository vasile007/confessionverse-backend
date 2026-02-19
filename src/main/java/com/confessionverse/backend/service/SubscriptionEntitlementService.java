package com.confessionverse.backend.service;

import com.confessionverse.backend.model.Subscription;
import com.confessionverse.backend.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionEntitlementService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionEntitlementService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public boolean isPro(Long userId) {
        if (userId == null) {
            return false;
        }
        return subscriptionRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .map(subscription -> {
                    String status = subscription.getStatus();
                    return "active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status);
                })
                .orElse(false);
    }

    public String getPlan(Long userId) {
        return isPro(userId) ? "PRO" : "FREE";
    }

    public PlanSnapshot getPlanSnapshot(Long userId) {
        boolean pro = isPro(userId);
        return new PlanSnapshot(pro, pro ? "PRO" : "FREE");
    }

    public record PlanSnapshot(boolean premium, String planType) {
    }
}
