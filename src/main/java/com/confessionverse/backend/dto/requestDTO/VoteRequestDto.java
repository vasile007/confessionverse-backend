package com.confessionverse.backend.dto.requestDTO;

import com.confessionverse.backend.model.VoteType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteRequestDto {

    @NotNull(message = "Vote type must not be null")
    private VoteType voteType;


    public boolean isLiked() {
        return this.voteType == VoteType.LIKE;
    }
}
