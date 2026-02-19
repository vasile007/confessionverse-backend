package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.Boost;
import com.confessionverse.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoostRepository extends JpaRepository<Boost, Long> {
    List<Boost> findByUserId(Long userId);
    List<Boost> findByConfessionId(Long confessionId);
    void deleteAllByUserId(Long userId);
    void deleteAllByConfessionId(Long confessionId);
    List<Boost> findByUser(User user);

}

