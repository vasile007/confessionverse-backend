package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, Long> {
    boolean existsByEventId(String eventId);
}
