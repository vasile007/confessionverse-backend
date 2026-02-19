package com.confessionverse.backend.model;

import com.confessionverse.backend.security.ownership.Ownable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "confession_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"confession_id", "reporter_user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfessionReport  implements Ownable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporterUser;

    @Column(name = "report_reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "description", length = 2000)  // optional, poate să fie null
    private String description;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "reporter_ip", nullable = false, length = 255)
    private String reporterIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confession_id", nullable = false)
    private Confession confession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;



    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReportStatus.PENDING;
        }
    }

    @Override
    public Long getOwnerId() {
        return reporter != null ? reporter.getId() : null;
    }
}



