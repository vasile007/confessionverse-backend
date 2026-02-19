package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfessionReportRequestDto {

    private Long confessionId;

    @NotBlank(message = "Reason must not be blank")
    private String reason; // 🔁 redenumit pentru consistență

    private String description;
    private String severity;
    private String reporterIp;
    private Long reporterUserId;
}


