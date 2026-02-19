package com.confessionverse.backend.repository;


import com.confessionverse.backend.model.ConfessionVote;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.model.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConfessionVoteRepository extends JpaRepository<ConfessionVote, Long> {

    Optional<ConfessionVote> findByConfessionIdAndVoterIp(Long confessionId, String voterIp);
    Optional<ConfessionVote> findByConfessionIdAndVoterId(Long confessionId, Long voterId);
    Optional<ConfessionVote> findTopByConfessionIdAndVoterIdOrderByIdDesc(Long confessionId, Long voterId);

    int countByConfessionIdAndVoteType(Long confessionId, VoteType voteType);
    Optional<ConfessionVote> findByConfessionIdAndVoter(Long confessionId, User voter);
    void deleteAllByConfessionId(Long confessionId);
    void deleteAllByVoterId(Long voterId);


}



