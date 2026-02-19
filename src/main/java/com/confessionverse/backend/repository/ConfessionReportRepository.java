package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.ConfessionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import com.confessionverse.backend.model.User;


import java.util.List;

public interface ConfessionReportRepository extends JpaRepository<ConfessionReport, Long> {

    List<ConfessionReport> findAllByConfessionId(Long confessionId);

    boolean existsByConfessionIdAndReporterIp(Long confessionId, String reporterIp);
    boolean existsByConfessionIdAndReporterUser(Long confessionId, User reporterUser);

    List<ConfessionReport> findAllByReporterUser(User reporterUser);
    void deleteAllByConfessionId(Long confessionId);
    void deleteAllByReporterUserId(Long reporterUserId);
    void deleteAllByReporterId(Long reporterId);
}


