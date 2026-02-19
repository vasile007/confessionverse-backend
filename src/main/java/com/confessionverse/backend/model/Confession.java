
package com.confessionverse.backend.model;

import com.confessionverse.backend.security.ownership.Ownable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Data
public class Confession implements Ownable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "confession")
    @JsonIgnore
    private Set<Boost> boosts;

    @Column(name = "dislike_count", nullable = false)
    private Integer dislikeCount = 0;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "is_hidden", nullable = false)
    private Boolean hidden = false;

    @OneToMany(mappedBy = "confession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConfessionVote> votes;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Object getTimestamp() {
       return this.createdAt;

    }

    @Override
    public Long getOwnerId() {
        return user != null ? user.getId() : null;
    }
}
