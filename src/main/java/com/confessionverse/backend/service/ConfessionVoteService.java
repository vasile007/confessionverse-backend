package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.requestDTO.VoteRequestDto;
import com.confessionverse.backend.dto.responseDTO.VoteResponseDto;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.ConfessionVote;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.model.VoteType;
import com.confessionverse.backend.repository.ConfessionRepository;
import com.confessionverse.backend.repository.ConfessionVoteRepository;

import com.confessionverse.backend.security.ownership.OwnableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ConfessionVoteService  implements OwnableService<ConfessionVote> {

    private static final Logger logger = LoggerFactory.getLogger(ConfessionVoteService.class);

    private final ConfessionVoteRepository confessionVoteRepository;
    private final ConfessionRepository confessionRepository;

    public ConfessionVoteService(ConfessionVoteRepository confessionVoteRepository, ConfessionRepository confessionRepository) {
        this.confessionVoteRepository = confessionVoteRepository;
        this.confessionRepository = confessionRepository;
    }

    @Transactional
    public VoteResponseDto vote(Long confessionId, String voterIp, VoteRequestDto voteRequest, User voterUser) {

        if (voteRequest == null || voteRequest.getVoteType() == null) {
            throw new IllegalArgumentException("voteType is required");
        }

        VoteType voteType = voteRequest.getVoteType();

        Confession confession = confessionRepository.findById(confessionId)
                .orElseThrow(() -> new IllegalArgumentException("Confession not found"));

        Optional<ConfessionVote> existingVote;

        if (voterUser != null) {
            // 🔵 USER AUTENTIFICAT → vot DOAR pe user
            existingVote = confessionVoteRepository
                    .findByConfessionIdAndVoter(confessionId, voterUser);
        } else {
            // 🟡 ANONIM → vot DOAR pe IP
            if (voterIp == null || voterIp.isBlank()) {
                throw new IllegalArgumentException("Missing voter IP");
            }
            existingVote = confessionVoteRepository
                    .findByConfessionIdAndVoterIp(confessionId, voterIp);
        }

        ConfessionVote vote;

        if (existingVote.isPresent()) {
            vote = existingVote.get();

            if (vote.getVoteType() == voteType) {
                throw new IllegalArgumentException("You already voted this way");
            }

            vote.setVoteType(voteType);

        } else {
            vote = new ConfessionVote();
            vote.setConfession(confession);
            vote.setVoteType(voteType);

            if (voterUser != null) {
                vote.setVoter(voterUser);
                vote.setVoterIp(null);   // 🔥 regula de aur
            } else {
                vote.setVoter(null);
                vote.setVoterIp(voterIp);
            }
        }

        confessionVoteRepository.save(vote);

        return buildVoteResponse(confessionId, voterIp, voteType);
    }


    public VoteResponseDto getVotesSummary(Long confessionId) {
        int likeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.LIKE);
        int dislikeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.DISLIKE);

        return VoteResponseDto.builder()
                .confessionId(confessionId)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .message("Summary retrieved successfully")
                .build();
    }

    public VoteResponseDto getUserVoteInfo(Long confessionId, User voterUser) {

        int likeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.LIKE);
        int dislikeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.DISLIKE);

        Boolean liked = null;

        if (voterUser != null) {
            Optional<ConfessionVote> voteOpt =
                    confessionVoteRepository.findByConfessionIdAndVoter(confessionId, voterUser);
            if (voteOpt.isPresent()) {
                liked = voteOpt.get().getVoteType() == VoteType.LIKE;
            }
        }

        return VoteResponseDto.builder()
                .confessionId(confessionId)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .liked(liked)
                .message("User vote info retrieved successfully")
                .build();
    }

    // helper method să nu repeți codul
    private VoteResponseDto buildVoteResponse(Long confessionId, String voterIp, VoteType voteType) {
        int likeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.LIKE);
        int dislikeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.DISLIKE);

        Boolean liked = voteType == VoteType.LIKE;

        return VoteResponseDto.builder()
                .confessionId(confessionId)
                .voterIp(voterIp)
                .liked(liked)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .message("Vote processed successfully")
                .build();
    }


    @Override
    public Optional<ConfessionVote> getById(Long id) {
        return Optional.ofNullable(confessionVoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ConfessionVote not found")));
    }

    @Override
    public Class<ConfessionVote> getEntityClass() {
        return ConfessionVote.class;
    }

    public Long getConfessionIdByVoteId(Long voteId) {
        return confessionVoteRepository.findById(voteId)
                .map(vote -> vote.getConfession().getId())
                .orElseThrow(() -> new ResourceNotFoundException("ConfessionVote not found with id: " + voteId));
    }

    public VoteResponseDto getVoteById(Long voteId) {
        ConfessionVote vote = confessionVoteRepository.findById(voteId)
                .orElseThrow(() -> new ResourceNotFoundException("ConfessionVote not found with id: " + voteId));

        Long confessionId = vote.getConfession().getId();
        int likeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.LIKE);
        int dislikeCount = confessionVoteRepository.countByConfessionIdAndVoteType(confessionId, VoteType.DISLIKE);

        return VoteResponseDto.builder()
                .confessionId(confessionId)
                .voterIp(vote.getVoterIp())
                .liked(vote.getVoteType() == VoteType.LIKE)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .message("Vote retrieved successfully")
                .build();
    }

}





