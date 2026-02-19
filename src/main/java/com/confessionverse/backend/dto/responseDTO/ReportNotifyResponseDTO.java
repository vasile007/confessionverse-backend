package com.confessionverse.backend.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportNotifyResponseDTO {
    private Long reportId;
    private String status;
    private boolean emailed;
    private String recipientEmail;
    private String message;
    private String reason;
}
