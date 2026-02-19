package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportStatusUpdateRequestDTO {
    @NotBlank(message = "status is required")
    private String status;

    private String adminNote;
}
