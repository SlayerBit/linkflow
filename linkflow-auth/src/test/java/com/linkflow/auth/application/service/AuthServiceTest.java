package com.linkflow.auth.application.service;

import com.linkflow.auth.api.dto.LoginRequest;
import com.linkflow.auth.api.dto.RegisterRequest;
import com.linkflow.auth.domain.exception.InvalidCredentialsException;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.UserPrincipalData;
import com.linkflow.common.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userLookupPort, jwtService, refreshTokenService, passwordEncoder);
    }

    @Test
    void register_createsUserAndReturnsResponse() {
        when(userLookupPort.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongP@ss1")).thenReturn("hashed");
        UUID userId = UUID.randomUUID();
        when(userLookupPort.createUser(any())).thenReturn(new UserPrincipalData(
                userId, "new@example.com", "hashed", "New", "User",
                Set.of(SecurityConstants.ROLE_USER), true));

        RegisterRequest request = new RegisterRequest(
                "new@example.com", "StrongP@ss1", "New", "User");
        var response = authService.register(request);

        assertEquals("new@example.com", response.getEmail());
        assertEquals(userId, response.getId());
        verify(userLookupPort).createUser(any());
    }

    @Test
    void register_duplicateEmailThrowsConflict() {
        when(userLookupPort.existsByEmail("exists@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest(
                "exists@example.com", "StrongP@ss1", "Exists", "User");
        assertThrows(ConflictException.class, () -> authService.register(request));
    }

    @Test
    void login_invalidPasswordThrows() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("user@example.com", "wrong")));
    }

    @Test
    void login_successReturnsTokens() {
        UUID userId = UUID.randomUUID();
        when(userLookupPort.findByEmail("user@example.com")).thenReturn(Optional.of(
                new UserPrincipalData(userId, "user@example.com", "hash", "U", "S",
                        Set.of(SecurityConstants.ROLE_USER), true)));
        when(passwordEncoder.matches("StrongP@ss1", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any())).thenReturn("access-jwt");
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("refresh-opaque");
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        var response = authService.login(new LoginRequest("user@example.com", "StrongP@ss1"));

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("refresh-opaque", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
    }
}
