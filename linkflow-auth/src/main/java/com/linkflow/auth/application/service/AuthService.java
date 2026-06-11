package com.linkflow.auth.application.service;

import com.linkflow.auth.api.dto.*;
import com.linkflow.auth.domain.exception.InvalidCredentialsException;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.CreateUserCommand;
import com.linkflow.common.port.UserLookupPort.UserPrincipalData;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import com.linkflow.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

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

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userLookupPort.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        CreateUserCommand command = new CreateUserCommand(
                request.getEmail(),
                hashedPassword,
                request.getFirstName(),
                request.getLastName(),
                Set.of(SecurityConstants.ROLE_USER)
        );

        UserPrincipalData user = userLookupPort.createUser(command);
        log.info("User registered: email={}", user.email());

        return RegisterResponse.builder()
                .id(user.id())
                .email(user.email())
                .firstName(user.firstName())
                .lastName(user.lastName())
                .roles(user.roles())
                .createdAt(Instant.now())
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        UserPrincipalData user = userLookupPort.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.enabled()) {
            throw new InvalidCredentialsException();
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
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
        log.info("User logged out");
    }
}
