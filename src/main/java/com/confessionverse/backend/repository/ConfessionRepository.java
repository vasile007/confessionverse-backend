package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConfessionRepository extends JpaRepository<Confession, Long> {
    List<Confession> findByUserId(Long userId);
    void deleteAllByUserId(Long userId);

    List<Confession> findAllByOrderByCreatedAtDesc();
    List<Confession> findByUserOrderByCreatedAtDesc(User user);
    @Query("SELECT c FROM Confession c JOIN FETCH c.user u WHERE lower(u.username) = lower(:username) AND c.hidden = false ORDER BY c.createdAt DESC")
    List<Confession> findByUserUsernameIgnoreCaseOrderByCreatedAtDesc(@Param("username") String username);

    @Query("SELECT c FROM Confession c JOIN FETCH c.user WHERE c.hidden = false ORDER BY c.createdAt DESC")
    List<Confession> findAllPublicWithUserOrderByCreatedAtDesc();
}

