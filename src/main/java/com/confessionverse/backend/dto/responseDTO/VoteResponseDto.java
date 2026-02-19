package com.confessionverse.backend.dto.responseDTO;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteResponseDto {
    private Long confessionId;
    private Boolean liked;
    private String voterIp;
    private int likeCount;
    private int dislikeCount;
    private String message;
}
