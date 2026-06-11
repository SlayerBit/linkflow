package com.linkflow.auth.application.service;

import com.linkflow.auth.infrastructure.config.JwtProperties;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * JWT generation and validation service using HMAC-SHA512.
 */
@Slf4j
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate an access token for the given user principal.
     */
    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessExpirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getEmail())
                .claim(SecurityConstants.CLAIM_USER_ID, principal.getId().toString())
                .claim(SecurityConstants.CLAIM_EMAIL, principal.getEmail())
                .claim(SecurityConstants.CLAIM_ROLES, new ArrayList<>(principal.getRoles()))
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate a JWT, returning the claims.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validate the token and return true if valid, false otherwise.
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract user ID from token claims.
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return UUID.fromString(claims.get(SecurityConstants.CLAIM_USER_ID, String.class));
    }

    /**
     * Extract email from token claims.
     */
    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * Extract roles from token claims.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
        return new HashSet<>(roles);
    }

    public long getAccessExpirationMs() {
        return jwtProperties.getAccessExpirationMs();
    }
}
