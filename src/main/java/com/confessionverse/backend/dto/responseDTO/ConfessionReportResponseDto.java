package com.confessionverse.backend.dto.responseDTO;

import lombok.*;

import java.time.LocalDateTime;
@Data
@Builder
public class ConfessionReportResponseDto {
    private Long id;
    private String reason;
    private String description;
    private String severity;
    private String status;
    private String reporterIp;
    private Long confessionId;
    private Long reporterUserId;
    private String reporterEmail;
    private LocalDateTime createdAt;
}
