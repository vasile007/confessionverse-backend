package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.ConfessionReportRequestDto;
import com.confessionverse.backend.dto.requestDTO.ReportNotifyRequestDTO;
import com.confessionverse.backend.dto.requestDTO.ReportStatusUpdateRequestDTO;
import com.confessionverse.backend.dto.responseDTO.ConfessionReportResponseDto;
import com.confessionverse.backend.dto.responseDTO.ReportNotifyResponseDTO;
import com.confessionverse.backend.mapper.ConfessionReportMapper;
import com.confessionverse.backend.model.ConfessionReport;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.service.ConfessionReportService;
import com.confessionverse.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ConfessionReportController {

    private final ConfessionReportService reportService;
    private final ConfessionReportMapper reportMapper;
    private final UserService userService;

    public ConfessionReportController(ConfessionReportService reportService,
                                      ConfessionReportMapper reportMapper,
                                      UserService userService) {
        this.reportService = reportService;
        this.reportMapper = reportMapper;
        this.userService = userService;
    }

    @PostMapping("/{confessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> reportConfession(
            @PathVariable Long confessionId,
            @Valid @RequestBody ConfessionReportRequestDto reportRequest,
            Authentication authentication,
            HttpServletRequest request) {

        User user = userService.getUserEntityByEmail(authentication.getName());

        String message = reportService.reportConfession(confessionId, user, reportRequest, extractClientIp(request));

        return ResponseEntity.ok(message);
    }

    @GetMapping("/{confessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConfessionReportResponseDto>> getReportsForConfession(@PathVariable Long confessionId) {
        List<ConfessionReport> reports = reportService.getReportsByConfessionId(confessionId);
        List<ConfessionReportResponseDto> dtos = reports.stream()
                .map(reportMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // Removed extractClientIp method because it is unused

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConfessionReportResponseDto>> getAllReports() {
        List<ConfessionReport> reports = reportService.getAllReports();
        List<ConfessionReportResponseDto> dtos = reports.stream()
                .map(reportMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConfessionReportResponseDto>> getMyReports(Authentication authentication) {
        User currentUser = userService.getUserEntityByEmail(authentication.getName());
        List<ConfessionReport> reports = reportService.getReportsForUser(currentUser);
        List<ConfessionReportResponseDto> dtos = reports.stream()
                .map(reportMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // Compatibility alias for older clients
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionReportResponseDto> updateStatusAlias(@PathVariable Long id,
                                                                         @RequestBody @Valid ReportStatusUpdateRequestDTO request) {
        ConfessionReport updated = reportService.updateReportStatusByAdmin(id, request.getStatus(), request.getAdminNote());
        return ResponseEntity.ok(reportMapper.toDto(updated));
    }

    // Compatibility alias for older clients still calling PUT /api/reports/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionReportResponseDto> updateStatusLegacyAlias(@PathVariable Long id,
                                                                                @RequestBody @Valid ReportStatusUpdateRequestDTO request) {
        ConfessionReport updated = reportService.updateReportStatusByAdmin(id, request.getStatus(), request.getAdminNote());
        return ResponseEntity.ok(reportMapper.toDto(updated));
    }

    // Compatibility alias for older clients
    @PostMapping("/{id}/email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportNotifyResponseDTO> notifyAlias(@PathVariable Long id,
                                                               @RequestBody @Valid ReportNotifyRequestDTO request) {
        return ResponseEntity.ok(reportService.notifyReportedUser(
                id,
                request.getStatus(),
                request.getAdminNote(),
                request.getSendEmail()
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}






