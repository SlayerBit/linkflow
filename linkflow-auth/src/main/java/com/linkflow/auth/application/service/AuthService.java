package com.linkflow.auth.application.service;

import com.linkflow.auth.api.dto.*;
import com.linkflow.auth.domain.exception.InvalidCredentialsException;
import com.linkflow.auth.domain.exception.EmailNotVerifiedException;
import com.linkflow.auth.domain.entity.EmailVerificationToken;
import com.linkflow.auth.domain.entity.PasswordResetToken;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.port.TokenRevocationPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.CreateUserCommand;
import com.linkflow.common.port.UserLookupPort.UserPrincipalData;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authentication service handling registration, login, refresh, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserLookupPort userLookupPort;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationPort tokenRevocationPort;
    private final com.linkflow.auth.domain.repository.EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final com.linkflow.auth.domain.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    private final com.linkflow.auth.infrastructure.config.LinkflowSecurityProperties securityProperties;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userLookupPort.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        boolean initialEmailVerifiedState = !securityProperties.isEmailVerificationRequired();

        CreateUserCommand command = new CreateUserCommand(
                request.getEmail(),
                hashedPassword,
                request.getFirstName(),
                request.getLastName(),
                Set.of(SecurityConstants.ROLE_USER),
                initialEmailVerifiedState
        );

        UserPrincipalData user = userLookupPort.createUser(command);
        log.info("User registered: email={}", user.email());

        String rawToken = null;
        if (securityProperties.isEmailVerificationRequired()) {
            // Generate email verification token
            rawToken = generateOpaqueToken();
            String hash = hashToken(rawToken);
            EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                    .tokenHash(hash)
                    .userId(user.id())
                    .expiresAt(Instant.now().plus(java.time.Duration.ofHours(24)))
                    .build();
            emailVerificationTokenRepository.save(verificationToken);
        }

        return RegisterResponse.builder()
                .id(user.id())
                .email(user.email())
                .firstName(user.firstName())
                .lastName(user.lastName())
                .roles(user.roles())
                .createdAt(Instant.now())
                .verificationToken(rawToken)
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        UserPrincipalData user = userLookupPort.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.enabled()) {
            throw new InvalidCredentialsException();
        }

        if (securityProperties.isEmailVerificationRequired() && !user.emailVerified()) {
            throw new EmailNotVerifiedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.roles(), user.enabled()
        );

        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user.id());

        log.info("User logged in: email={}", user.email());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RotationResult result =
                refreshTokenService.rotateRefreshToken(request.getRefreshToken());

        UserPrincipalData user = userLookupPort.findById(result.userId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.enabled()) {
            throw new InvalidCredentialsException();
        }

        if (securityProperties.isEmailVerificationRequired() && !user.emailVerified()) {
            throw new EmailNotVerifiedException();
        }

        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.roles(), user.enabled()
        );

        String accessToken = jwtService.generateAccessToken(principal);

        log.debug("Token refreshed for userId={}", result.userId());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(result.newRawToken())
                .expiresIn(jwtService.getAccessExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.getRefreshToken())
                .ifPresent(userId -> tokenRevocationPort.markAccessTokensRevokedAfter(userId, Instant.now()));
        log.info("User logged out");
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String hash = hashToken(rawToken);
        com.linkflow.auth.domain.entity.EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new com.linkflow.common.exception.ResourceNotFoundException("Verification token", "not found"));

        if (token.isUsed()) {
            throw new com.linkflow.common.exception.ConflictException("Token already used");
        }
        if (token.isExpired()) {
            throw new com.linkflow.common.exception.ConflictException("Token expired");
        }

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        userLookupPort.updateEmailVerified(token.getUserId(), true);
        log.info("Email verified for userId={}", token.getUserId());
    }

    @Transactional
    public java.util.Optional<String> requestPasswordReset(String email) {
        return userLookupPort.findByEmail(email).map(user -> {
            String rawToken = generateOpaqueToken();
            String hash = hashToken(rawToken);

            PasswordResetToken token = PasswordResetToken.builder()
                    .tokenHash(hash)
                    .userId(user.id())
                    .expiresAt(Instant.now().plus(java.time.Duration.ofMinutes(15)))
                    .build();
            passwordResetTokenRepository.save(token);

            log.info("Password reset token generated for userId={}", user.id());
            return rawToken;
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = hashToken(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new com.linkflow.common.exception.ResourceNotFoundException("Reset token", "not found"));

        if (token.isUsed()) {
            throw new com.linkflow.common.exception.ConflictException("Token already used");
        }
        if (token.isExpired()) {
            throw new com.linkflow.common.exception.ConflictException("Token expired");
        }

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        userLookupPort.updatePasswordHash(token.getUserId(), passwordEncoder.encode(newPassword));

        refreshTokenService.revokeAllForUser(token.getUserId());
        tokenRevocationPort.markAccessTokensRevokedAfter(token.getUserId(), Instant.now());

        log.info("Password reset successfully for userId={}", token.getUserId());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        UserPrincipalData user = userLookupPort.findById(principal.getId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.passwordHash())) {
            throw new ConflictException("New password must differ from the current password");
        }

        userLookupPort.updatePasswordHash(user.id(), passwordEncoder.encode(request.getNewPassword()));
        Instant now = Instant.now();
        refreshTokenService.revokeAllForUser(user.id());
        tokenRevocationPort.markAccessTokensRevokedAfter(user.id(), now);
        log.info("Password changed for userId={}", user.id());
    }
}
