package com.confessionverse.backend.mapper;


import com.confessionverse.backend.dto.requestDTO.ConfessionReportRequestDto;
import com.confessionverse.backend.dto.responseDTO.ConfessionReportResponseDto;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.ConfessionReport;
import com.confessionverse.backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConfessionReportMapper {

    private static final Logger logger = LoggerFactory.getLogger(ConfessionReportMapper.class);


    public ConfessionReport toEntity(ConfessionReportRequestDto dto, User reporter, Confession confession) {
        return ConfessionReport.builder()
                .reason(dto.getReason())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .reporterIp(dto.getReporterIp())
                .reporterUser(reporter)
                .confession(confession)
                .build();
    }

    public ConfessionReportResponseDto toDto(ConfessionReport entity) {
        String reporterEmail = "";
        if (entity.getReporterUser() != null) {
            reporterEmail = entity.getReporterUser().getEmail();
            if (reporterEmail == null) {
                logger.warn("Reporter email missing for report {}", entity.getId());
                reporterEmail = "";
            }
        }

        return ConfessionReportResponseDto.builder()
                .id(entity.getId())
                .reason(entity.getReason())
                .description(entity.getDescription())
                .severity(entity.getSeverity())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .reporterIp(entity.getReporterIp())
                .createdAt(entity.getCreatedAt())
                .confessionId(entity.getConfession().getId())
                .reporterUserId(entity.getReporterUser() != null ? entity.getReporterUser().getId() : null)
                .reporterEmail(reporterEmail)
                .build();
    }
}



