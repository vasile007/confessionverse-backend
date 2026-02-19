package com.confessionverse.backend.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportNotifyRequestDTO {
    @NotBlank(message = "status is required")
    private String status;

    @NotBlank(message = "adminNote is required")
    private String adminNote;

    private Boolean sendEmail = true;
}
