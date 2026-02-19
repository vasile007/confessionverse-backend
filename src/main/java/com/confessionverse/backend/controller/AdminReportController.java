package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.ReportNotifyRequestDTO;
import com.confessionverse.backend.dto.requestDTO.ReportStatusUpdateRequestDTO;
import com.confessionverse.backend.dto.responseDTO.ConfessionReportResponseDto;
import com.confessionverse.backend.dto.responseDTO.ReportNotifyResponseDTO;
import com.confessionverse.backend.mapper.ConfessionReportMapper;
import com.confessionverse.backend.model.ConfessionReport;
import com.confessionverse.backend.service.ConfessionReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@CrossOrigin(origins = "*")
public class AdminReportController {

    private final ConfessionReportService reportService;
    private final ConfessionReportMapper reportMapper;

    public AdminReportController(ConfessionReportService reportService, ConfessionReportMapper reportMapper) {
        this.reportService = reportService;
        this.reportMapper = reportMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConfessionReportResponseDto>> getAllReports() {
        List<ConfessionReportResponseDto> dtos = reportService.getAllReports().stream()
                .map(reportMapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionReportResponseDto> updateStatusPut(@PathVariable Long id,
                                                                       @RequestBody @Valid ReportStatusUpdateRequestDTO request) {
        ConfessionReport updated = reportService.updateReportStatusByAdmin(id, request.getStatus(), request.getAdminNote());
        return ResponseEntity.ok(reportMapper.toDto(updated));
    }

    // Compatibility alias for clients still calling PUT /api/admin/reports/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionReportResponseDto> updateStatusLegacy(@PathVariable Long id,
                                                                          @RequestBody @Valid ReportStatusUpdateRequestDTO request) {
        ConfessionReport updated = reportService.updateReportStatusByAdmin(id, request.getStatus(), request.getAdminNote());
        return ResponseEntity.ok(reportMapper.toDto(updated));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfessionReportResponseDto> updateStatusPatch(@PathVariable Long id,
                                                                         @RequestBody @Valid ReportStatusUpdateRequestDTO request) {
        ConfessionReport updated = reportService.updateReportStatusByAdmin(id, request.getStatus(), request.getAdminNote());
        return ResponseEntity.ok(reportMapper.toDto(updated));
    }

    @PostMapping("/{id}/notify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportNotifyResponseDTO> notifyUser(@PathVariable Long id,
                                                              @RequestBody @Valid ReportNotifyRequestDTO request) {
        ReportNotifyResponseDTO response = reportService.notifyReportedUser(
                id,
                request.getStatus(),
                request.getAdminNote(),
                request.getSendEmail()
        );
        return ResponseEntity.ok(response);
    }

    // Compatibility alias for clients still calling POST /api/admin/reports/{id}/email
    @PostMapping("/{id}/email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportNotifyResponseDTO> notifyUserLegacy(@PathVariable Long id,
                                                                    @RequestBody @Valid ReportNotifyRequestDTO request) {
        return notifyUser(id, request);
    }
}
