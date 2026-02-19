package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.PasswordResetToken;
import com.confessionverse.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    boolean existsByToken(String token);
    List<PasswordResetToken> findAllByUserAndUsedAtIsNullAndExpiresAtAfter(User user, LocalDateTime threshold);
}
