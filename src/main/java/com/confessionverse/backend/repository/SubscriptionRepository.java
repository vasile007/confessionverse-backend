
package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    void deleteAllByUserId(Long userId);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Subscription> findTopByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Subscription> findTopByStripeCustomerIdOrderByUpdatedAtDesc(String stripeCustomerId);
}
