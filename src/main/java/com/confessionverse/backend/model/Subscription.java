package com.confessionverse.backend.model;


import com.confessionverse.backend.security.ownership.Ownable;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
public class Subscription implements Ownable {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String planType;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(length = 255)
    private String stripeCustomerId;

    @Column(length = 255, unique = true)
    private String stripeSubscriptionId;

    @Column(length = 50)
    private String status;

    private LocalDateTime currentPeriodStart;

    private LocalDateTime currentPeriodEnd;

    private Boolean cancelAtPeriodEnd = false;

    private LocalDateTime lastPaymentAt;

    @Column(length = 255)
    private String lastInvoiceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id") // numele coloanei poate fi diferit, vezi schema ta DB
    private User subscriber;


    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public Long getOwnerId() {
        if (subscriber != null) {
            return subscriber.getId();
        }
        return user != null ? user.getId() : null;
    }
}
