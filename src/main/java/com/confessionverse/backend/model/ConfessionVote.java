package com.confessionverse.backend.model;


import com.confessionverse.backend.security.ownership.Ownable;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "confession_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfessionVote  implements Ownable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private VoteType voteType;

    @Column(name = "voter_ip", nullable = true, length = 255)
    private String voterIp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confession_id", nullable = false)
    private Confession confession;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id", nullable = true)
    private User voter;



    public ConfessionVote(Confession confession, String voterIp, VoteType voteType) {
        this.confession = confession;
        this.voterIp = voterIp;
        this.voteType = voteType;
    }

    @Override
    public Long getOwnerId() {
        return voter != null ? voter.getId() : null;
    }
}

