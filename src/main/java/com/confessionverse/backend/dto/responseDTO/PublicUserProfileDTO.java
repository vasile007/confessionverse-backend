package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileDTO {
    private Long id;
    private String username;
    private Boolean premium;
    private String planType;
    private String avatar;
    private String bio;
    private Stats stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private long totalLikes;
        private long totalDislikes;
    }
}
