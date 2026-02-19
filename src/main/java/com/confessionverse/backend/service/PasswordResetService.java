package com.confessionverse.backend.service;

import com.confessionverse.backend.model.PasswordResetToken;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.PasswordResetTokenRepository;
import com.confessionverse.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int TOKEN_SIZE_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService passwordResetEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.token-ttl-minutes:60}")
    private long tokenTtlMinutes;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordResetEmailService passwordResetEmailService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEmailService = passwordResetEmailService;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("No account found for this email.");
        }

        User user = userOptional.get();
        LocalDateTime now = LocalDateTime.now();
        invalidateActiveTokens(user, now);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(generateUniqueToken());
        resetToken.setUser(user);
        resetToken.setExpiresAt(now.plusMinutes(tokenTtlMinutes));

        passwordResetTokenRepository.save(resetToken);

        passwordResetEmailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        LocalDateTime now = LocalDateTime.now();
        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);
        invalidateActiveTokens(user, now);
    }

    private void invalidateActiveTokens(User user, LocalDateTime now) {
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository
                .findAllByUserAndUsedAtIsNullAndExpiresAtAfter(user, now);
        for (PasswordResetToken activeToken : activeTokens) {
            activeToken.setUsedAt(now);
        }
        if (!activeTokens.isEmpty()) {
            passwordResetTokenRepository.saveAll(activeTokens);
        }
    }

    private String generateUniqueToken() {
        String token = generateSecureToken();
        while (passwordResetTokenRepository.existsByToken(token)) {
            token = generateSecureToken();
        }
        return token;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
