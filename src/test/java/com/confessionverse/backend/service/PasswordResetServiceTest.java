package com.confessionverse.backend.service;

import com.confessionverse.backend.model.PasswordResetToken;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.PasswordResetTokenRepository;
import com.confessionverse.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordResetEmailService passwordResetEmailService;

    private PasswordResetService passwordResetService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordResetService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                passwordResetEmailService
        );
        ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 60L);
    }

    @Test
    void resetPasswordWithValidTokenShouldChangePasswordHashAndInvalidateToken() {
        User user = new User();
        user.setId(10L);
        String oldHash = passwordEncoder.encode("oldPassword123");
        user.setPasswordHash(oldHash);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("validToken");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(passwordResetTokenRepository.findByToken("validToken")).thenReturn(Optional.of(token));
        when(passwordResetTokenRepository.findAllByUserAndUsedAtIsNullAndExpiresAtAfter(eq(user), any(LocalDateTime.class)))
                .thenReturn(List.of());

        passwordResetService.resetPassword("validToken", "newPassword123");

        assertNotEquals(oldHash, user.getPasswordHash());
        assertTrue(passwordEncoder.matches("newPassword123", user.getPasswordHash()));
        assertNotNull(token.getUsedAt());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordWithExpiredTokenShouldFail() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expiredToken");
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        token.setUsedAt(null);

        when(passwordResetTokenRepository.findByToken("expiredToken")).thenReturn(Optional.of(token));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword("expiredToken", "newPassword123")
        );

        assertEquals("Invalid or expired token", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordWithUsedTokenShouldFail() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("usedToken");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        token.setUsedAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByToken("usedToken")).thenReturn(Optional.of(token));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword("usedToken", "newPassword123")
        );

        assertEquals("Invalid or expired token", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
