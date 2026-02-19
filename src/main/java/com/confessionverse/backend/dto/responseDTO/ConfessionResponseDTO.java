package com.confessionverse.backend.dto.responseDTO;

import lombok.Data;

@Data
public class ConfessionResponseDTO {
    private Long id;
    private String content;
    private String timestamp;
    private Long userId;
    private String username;
    private String author;
    private UserSummaryDTO user;
    private UserSummaryDTO owner;
    private UserSummaryDTO authorInfo;
    private Boolean premiumHighlight;
    private Boolean highlighted;
    private Boolean isPremium;
    private Boolean hidden;

}
