package com.linkflow.auth.application.service;

import com.linkflow.auth.infrastructure.config.JwtProperties;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Issues and validates access tokens, signed with HMAC-SHA512.
 * <p>
 * The algorithm is pinned on both sides rather than inferred. Accepting whatever a token's header
 * declares is the root of the classic JWT confusion attacks, so verification here will only admit
 * an HS512 signature over the configured key. Issuer and audience are likewise required, not
 * merely emitted.
 */
@Slf4j
@Service
public class JwtService {

    private static final MacAlgorithm SIGNATURE_ALGORITHM = Jwts.SIG.HS512;

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .sig().add(SIGNATURE_ALGORITHM).and()
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .clockSkewSeconds(jwtProperties.getClockSkewSeconds())
                .build();
    }

    /**
     * Generate an access token for the given user principal.
     */
    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessExpirationMs());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .subject(principal.getEmail())
                .claim(SecurityConstants.CLAIM_USER_ID, principal.getId().toString())
                .claim(SecurityConstants.CLAIM_EMAIL, principal.getEmail())
                .claim(SecurityConstants.CLAIM_ROLES, new ArrayList<>(principal.getRoles()))
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    /**
     * Parse and validate a JWT, returning the claims.
     */
    public Claims parseToken(String token) {
        return parser.parseSignedClaims(token).getPayload();
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
