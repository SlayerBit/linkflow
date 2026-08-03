package com.linkflow.auth.application.service;

import com.linkflow.auth.api.dto.LoginRequest;
import com.linkflow.auth.api.dto.RegisterRequest;
import com.linkflow.auth.domain.entity.EmailVerificationToken;
import com.linkflow.auth.domain.exception.InvalidCredentialsException;
import com.linkflow.auth.domain.repository.EmailVerificationTokenRepository;
import com.linkflow.auth.domain.repository.PasswordResetTokenRepository;
import com.linkflow.auth.infrastructure.config.LinkflowSecurityProperties;
import com.linkflow.common.event.EmailRequestedEvent;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.mail.MailSendCooldown;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.common.port.TokenRevocationPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.UserPrincipalData;
import com.linkflow.common.security.SecureTokenGenerator;
import com.linkflow.common.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserLookupPort userLookupPort;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenRevocationPort tokenRevocationPort;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private LinkflowSecurityProperties securityProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MailSendCooldown mailSendCooldown;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(securityProperties.isEmailVerificationRequired()).thenReturn(true);
        lenient().when(mailSendCooldown.tryAcquire(any(), anyString())).thenReturn(true);
        authService = new AuthService(
                userLookupPort, jwtService, refreshTokenService, passwordEncoder, tokenRevocationPort,
                emailVerificationTokenRepository, passwordResetTokenRepository, securityProperties,
                new SecureTokenGenerator(), eventPublisher, mailSendCooldown, LinkflowMetrics.noop());
    }

    @Test
    void register_createsUserAndReturnsResponse() {
        UUID userId = stubSuccessfulRegistration();

        var response = authService.register(new RegisterRequest(
                "new@example.com", "StrongP@ss1", "New", "User"));

        assertEquals("new@example.com", response.getEmail());
        assertEquals(userId, response.getId());
        verify(userLookupPort).createUser(any());
    }

    @Test
    void register_requestsVerificationEmailWithTokenMatchingStoredHash() {
        stubSuccessfulRegistration();

        authService.register(new RegisterRequest("new@example.com", "StrongP@ss1", "New", "User"));

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(saved.capture());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());

        assertInstanceOf(EmailRequestedEvent.EmailVerificationRequested.class, published.getValue());
        var event = (EmailRequestedEvent.EmailVerificationRequested) published.getValue();
        assertEquals("new@example.com", event.recipient());

        // The emailed token must be the pre-image of the persisted hash, or the link cannot redeem.
        assertEquals(saved.getValue().getTokenHash(), new SecureTokenGenerator().hash(event.rawToken()));
        // Raw tokens must never be persisted.
        assertNotEquals(event.rawToken(), saved.getValue().getTokenHash());
    }

    @Test
    void register_invalidatesAnyEarlierVerificationToken() {
        stubSuccessfulRegistration();

        authService.register(new RegisterRequest("new@example.com", "StrongP@ss1", "New", "User"));

        verify(emailVerificationTokenRepository).markAllUnusedAsUsedForUser(any(UUID.class));
    }

    @Test
    void register_skipsVerificationEmailWhenVerificationNotRequired() {
        when(securityProperties.isEmailVerificationRequired()).thenReturn(false);
        stubSuccessfulRegistration();

        authService.register(new RegisterRequest("new@example.com", "StrongP@ss1", "New", "User"));

        verifyNoInteractions(emailVerificationTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void register_duplicateEmailThrowsConflict() {
        when(userLookupPort.existsByEmail("exists@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest(
                "exists@example.com", "StrongP@ss1", "Exists", "User");
        assertThrows(ConflictException.class, () -> authService.register(request));
    }

    @Test
    void requestPasswordReset_unknownEmailSucceedsWithoutSendingAnything() {
        when(userLookupPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Must not throw: a distinguishable response would let an attacker enumerate accounts.
        assertDoesNotThrow(() -> authService.requestPasswordReset("nobody@example.com"));

        verifyNoInteractions(passwordResetTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestPasswordReset_knownEmailInvalidatesEarlierTokensAndRequestsEmail() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));

        authService.requestPasswordReset("user@example.com");

        verify(passwordResetTokenRepository).markAllUnusedAsUsedForUser(userId);
        verify(passwordResetTokenRepository).save(any());
        verify(eventPublisher).publishEvent(any(EmailRequestedEvent.PasswordResetRequested.class));
    }

    @Test
    void resendVerificationEmail_alreadyVerifiedIssuesNothing() {
        when(userLookupPort.findByEmail("done@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(UUID.randomUUID(), "done@example.com", "hash", "D", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));

        authService.resendVerificationEmail("done@example.com");

        verifyNoInteractions(emailVerificationTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void resendVerificationEmail_unverifiedIssuesFreshToken() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("pending@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "pending@example.com", "hash", "P", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, false)));

        authService.resendVerificationEmail("pending@example.com");

        verify(emailVerificationTokenRepository).markAllUnusedAsUsedForUser(userId);
        verify(eventPublisher).publishEvent(any(EmailRequestedEvent.EmailVerificationRequested.class));
    }

    @Test
    void resendVerificationEmail_unknownEmailSucceedsSilently() {
        when(userLookupPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.resendVerificationEmail("nobody@example.com"));

        verifyNoInteractions(eventPublisher);
    }

    /**
     * The order matters more than the suppression: issuing a token invalidates the one already in
     * the user's inbox. Were the cooldown consulted after that, a throttled resend would destroy a
     * working link and send nothing to replace it, leaving the account unreachable until the
     * cooldown lapsed.
     */
    @Test
    void resendVerificationEmail_throttledLeavesTheExistingLinkAlone() {
        when(userLookupPort.findByEmail("pending@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(UUID.randomUUID(), "pending@example.com", "hash", "P", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, false)));
        when(mailSendCooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_VERIFICATION, "pending@example.com"))
                .thenReturn(false);

        // Silent, like every other outcome of this endpoint: reporting the throttle would reveal
        // that the address belongs to a real, unverified account.
        assertDoesNotThrow(() -> authService.resendVerificationEmail("pending@example.com"));

        verifyNoInteractions(emailVerificationTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestPasswordReset_throttledLeavesTheExistingLinkAlone() {
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(UUID.randomUUID(), "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));
        when(mailSendCooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, "user@example.com"))
                .thenReturn(false);

        assertDoesNotThrow(() -> authService.requestPasswordReset("user@example.com"));

        verifyNoInteractions(passwordResetTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    /**
     * A verification link is followed by mail scanners and prefetchers, so the token can be spent
     * before the recipient clicks. Once the account is verified the request has already been
     * satisfied, and reporting a conflict would send a user to fix something that is not broken.
     */
    @Test
    void verifyEmail_isIdempotentOnceTheAccountIsVerified() {
        UUID userId = UUID.randomUUID();
        SecureTokenGenerator generator = new SecureTokenGenerator();
        String rawToken = generator.generateToken();

        when(emailVerificationTokenRepository.findByTokenHash(generator.hash(rawToken)))
                .thenReturn(Optional.of(EmailVerificationToken.builder()
                        .tokenHash(generator.hash(rawToken))
                        .userId(userId)
                        .expiresAt(Instant.now().minusSeconds(1))
                        .used(true)
                        .build()));
        when(userLookupPort.findById(userId)).thenReturn(Optional.of(
                new UserPrincipalData(userId, "done@example.com", "hash", "D", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));

        // Spent and expired, yet still not an error, because the account is verified either way.
        assertDoesNotThrow(() -> authService.verifyEmail(rawToken));

        // Nothing to re-apply, so the account must not be written to again.
        verify(userLookupPort, never()).updateEmailVerified(any(), anyBoolean());
    }

    @Test
    void verifyEmail_spentTokenOnUnverifiedAccountStillConflicts() {
        UUID userId = UUID.randomUUID();
        SecureTokenGenerator generator = new SecureTokenGenerator();
        String rawToken = generator.generateToken();

        when(emailVerificationTokenRepository.findByTokenHash(generator.hash(rawToken)))
                .thenReturn(Optional.of(EmailVerificationToken.builder()
                        .tokenHash(generator.hash(rawToken))
                        .userId(userId)
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .used(true)
                        .build()));
        when(userLookupPort.findById(userId)).thenReturn(Optional.of(
                new UserPrincipalData(userId, "pending@example.com", "hash", "P", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, false)));

        assertThrows(ConflictException.class, () -> authService.verifyEmail(rawToken));
    }

    @Test
    void verifyEmail_expiredTokenOnUnverifiedAccountConflicts() {
        UUID userId = UUID.randomUUID();
        SecureTokenGenerator generator = new SecureTokenGenerator();
        String rawToken = generator.generateToken();

        when(emailVerificationTokenRepository.findByTokenHash(generator.hash(rawToken)))
                .thenReturn(Optional.of(EmailVerificationToken.builder()
                        .tokenHash(generator.hash(rawToken))
                        .userId(userId)
                        .expiresAt(Instant.now().minusSeconds(1))
                        .build()));
        when(userLookupPort.findById(userId)).thenReturn(Optional.of(
                new UserPrincipalData(userId, "pending@example.com", "hash", "P", "V",
                        Set.of(SecurityConstants.ROLE_USER), true, false)));

        assertThrows(ConflictException.class, () -> authService.verifyEmail(rawToken));
        verify(userLookupPort, never()).updateEmailVerified(any(), anyBoolean());
    }

    @Test
    void login_invalidPasswordThrows() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("user@example.com", "wrong")));
    }

    @Test
    void login_successReturnsTokens() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true, true)));
        when(passwordEncoder.matches("StrongP@ss1", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-jwt");
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("refresh-opaque");
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        var response = authService.login(new LoginRequest("user@example.com", "StrongP@ss1"));

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("refresh-opaque", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
    }

    private UUID stubSuccessfulRegistration() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongP@ss1")).thenReturn("hashed");
        when(userLookupPort.createUser(any())).thenReturn(new UserPrincipalData(
                userId, "new@example.com", "hashed", "New", "User",
                Set.of(SecurityConstants.ROLE_USER), true, true));
        return userId;
    }
}
