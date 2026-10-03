
package com.confessionverse.backend.repository;

import com.confessionverse.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;


import java.util.List;
import java.util.Optional;


    public interface UserRepository extends JpaRepository<User, Long> {
        Optional<User> findByUsername(String username);
        Optional<User> findByUsernameIgnoreCase(String username);
        Optional<User> findByEmail(String email);
        Optional<User> findByUsernameOrEmail(String username, String email);
        List<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT u FROM User u WHERE u.id = :userId")
        Optional<User> findByIdForUpdate(@Param("userId") Long userId);

        boolean existsByUsername(String username);
        boolean existsByEmail(String email);
    }
