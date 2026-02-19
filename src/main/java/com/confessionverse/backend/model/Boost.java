package com.confessionverse.backend.model;

import com.confessionverse.backend.security.ownership.Ownable;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Boost  implements Ownable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "confession_id")
    private Confession confession;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)  // <-- important să salvezi enum ca string în DB
    private BoostType boostType;

    private LocalDateTime date;

    @PrePersist
    protected void onCreate() {
        date = LocalDateTime.now();
    }


    @Override
    public Long getOwnerId() {
        return user != null ? user.getId() : null;
    }
}
