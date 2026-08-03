package com.linkflow.auth.application.service;

import com.linkflow.auth.api.dto.*;
import com.linkflow.auth.domain.exception.InvalidCredentialsException;
import com.linkflow.auth.domain.exception.EmailNotVerifiedException;
import com.linkflow.auth.domain.entity.EmailVerificationToken;
import com.linkflow.auth.domain.entity.PasswordResetToken;
import com.linkflow.auth.domain.repository.EmailVerificationTokenRepository;
import com.linkflow.auth.domain.repository.PasswordResetTokenRepository;
import com.linkflow.auth.infrastructure.config.LinkflowSecurityProperties;
import com.linkflow.common.event.EmailRequestedEvent;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.mail.MailSendCooldown;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.common.port.TokenRevocationPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.CreateUserCommand;
import com.linkflow.common.port.UserLookupPort.UserPrincipalData;
import com.linkflow.common.security.SecureTokenGenerator;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Authentication service handling registration, login, refresh, logout, and credential recovery.
 * <p>
 * Recovery flows persist only the SHA-256 hash of each single-use token and hand the raw value
 * to {@link EmailRequestedEvent} for delivery. The raw token is never returned through the API
 * and never logged, so possession of the mailbox is the only way to complete a flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(15);

    private final UserLookupPort userLookupPort;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationPort tokenRevocationPort;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final LinkflowSecurityProperties securityProperties;
    private final SecureTokenGenerator tokenGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final MailSendCooldown mailSendCooldown;
    private final LinkflowMetrics metrics;

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
        log.info("User registered: userId={}", user.id());

        if (securityProperties.isEmailVerificationRequired()) {
            issueVerificationToken(user);
        }

        metrics.registrationSucceeded();

        return RegisterResponse.builder()
                .id(user.id())
                .email(user.email())
                .firstName(user.firstName())
                .lastName(user.lastName())
                .roles(user.roles())
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Issues a fresh verification token and requests delivery. Any unused token already
     * outstanding for the user is invalidated so only the newest link works, which keeps the
     * resend flow from leaving several valid links in a mailbox.
     */
    private void issueVerificationToken(UserPrincipalData user) {
        emailVerificationTokenRepository.markAllUnusedAsUsedForUser(user.id());

        String rawToken = tokenGenerator.generateToken();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(tokenGenerator.hash(rawToken))
                .userId(user.id())
                .expiresAt(Instant.now().plus(EMAIL_VERIFICATION_TOKEN_TTL))
                .build());

        eventPublisher.publishEvent(new EmailRequestedEvent.EmailVerificationRequested(
                user.email(), user.firstName(), rawToken, EMAIL_VERIFICATION_TOKEN_TTL));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        UserPrincipalData user = userLookupPort.findByEmail(request.getEmail())
                .orElse(null);
        if (user == null) {
            metrics.loginFailed("invalid_credentials");
            throw new InvalidCredentialsException();
        }

        if (!user.enabled()) {
            // Same response as a bad password: reporting "disabled" would let an attacker confirm
            // that an account exists and learn that it was taken offline.
            metrics.loginFailed("invalid_credentials");
            throw new InvalidCredentialsException();
        }

        if (securityProperties.isEmailVerificationRequired() && !user.emailVerified()) {
            metrics.loginFailed("email_not_verified");
            throw new EmailNotVerifiedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.passwordHash())) {
            metrics.loginFailed("invalid_credentials");
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = new UserPrincipal(
                user.id(), user.email(), user.passwordHash(), user.roles(), user.enabled()
        );

        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user.id());

        metrics.loginSucceeded();
        log.info("User logged in: userId={}", user.id());
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

    /**
     * Activates an account from an emailed link.
     * <p>
     * Idempotent by design: once the account is verified, presenting the link again succeeds rather
     * than reporting that it was already used. Links in email are followed by more than the
     * recipient — mail clients prefetch them, corporate scanners and antivirus proxies open them to
     * check for malware, and chat clients fetch them to build previews. Any of those can redeem a
     * single-use token before the person ever clicks, and a strict reading would then greet the
     * actual user with an error about a link they had not yet used. The state they care about is
     * "my email is verified", and that is true either way.
     * <p>
     * Password reset gets the opposite treatment on purpose: there, the token authorises setting a
     * new secret, so honouring it twice would let a replayed link overwrite a password that had
     * since been changed.
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Verification link", "is not valid"));

        boolean alreadyVerified = userLookupPort.findById(token.getUserId())
                .map(UserPrincipalData::emailVerified)
                .orElse(false);

        if (alreadyVerified) {
            // Consume the link on the way out so a still-live token does not linger in the mailbox.
            if (!token.isUsed()) {
                token.setUsed(true);
                emailVerificationTokenRepository.save(token);
            }
            log.debug("Verification link presented for already-verified userId={}", token.getUserId());
            return;
        }

        if (token.isUsed()) {
            throw new ConflictException("This verification link has already been used. "
                    + "Request a new one to finish activating your account.");
        }
        if (token.isExpired()) {
            throw new ConflictException("This verification link has expired. "
                    + "Request a new one to finish activating your account.");
        }

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        userLookupPort.updateEmailVerified(token.getUserId(), true);
        log.info("Email verified for userId={}", token.getUserId());
    }

    /**
     * Re-sends the activation link for an unverified account.
     * <p>
     * Returns normally regardless of whether the address exists, is already verified, or is inside
     * its send cooldown. Reporting any of those would turn this endpoint into an account-existence
     * oracle, and it is reachable without authentication.
     */
    @Transactional
    public void resendVerificationEmail(String email) {
        userLookupPort.findByEmail(email).ifPresentOrElse(user -> {
            if (user.emailVerified()) {
                log.debug("Verification resend ignored for already-verified userId={}", user.id());
                return;
            }
            // Checked before issuing, never after: issuing invalidates whatever link is already
            // outstanding, so suppressing the send afterwards would kill the user's working link
            // and give them nothing in return.
            if (!mailSendCooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_VERIFICATION, user.email())) {
                log.debug("Verification resend suppressed by cooldown for userId={}", user.id());
                return;
            }
            issueVerificationToken(user);
            log.info("Verification email re-requested for userId={}", user.id());
        }, () -> log.debug("Verification resend requested for an address with no account"));
    }

    /**
     * Starts password recovery. Returns normally for unknown addresses, and for addresses inside
     * their cooldown, so the response cannot be used to enumerate registered accounts.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userLookupPort.findByEmail(email).ifPresentOrElse(user -> {
            // As with resend: the cooldown is consulted before any existing token is invalidated,
            // so a suppressed request leaves the previous link intact and usable.
            if (!mailSendCooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, user.email())) {
                log.debug("Password reset suppressed by cooldown for userId={}", user.id());
                return;
            }

            // Invalidate outstanding tokens so a stolen older link cannot be replayed after the
            // real owner requests a new one.
            passwordResetTokenRepository.markAllUnusedAsUsedForUser(user.id());

            String rawToken = tokenGenerator.generateToken();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .tokenHash(tokenGenerator.hash(rawToken))
                    .userId(user.id())
                    .expiresAt(Instant.now().plus(PASSWORD_RESET_TOKEN_TTL))
                    .build());

            eventPublisher.publishEvent(new EmailRequestedEvent.PasswordResetRequested(
                    user.email(), user.firstName(), rawToken, PASSWORD_RESET_TOKEN_TTL));

            log.info("Password reset requested for userId={}", user.id());
        }, () -> log.debug("Password reset requested for an address with no account"));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Reset link", "is not valid"));

        if (token.isUsed()) {
            throw new ConflictException("This reset link has already been used. "
                    + "Request a new one if you still need to change your password.");
        }
        if (token.isExpired()) {
            throw new ConflictException("This reset link has expired. Request a new one to continue.");
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
