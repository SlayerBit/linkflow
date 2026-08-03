package com.linkflow.auth.application.service;

import com.linkflow.auth.infrastructure.config.JwtProperties;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    /** Random 64-byte key; HS512 requires 512 bits. */
    private static final String SECRET =
            "giQ9CvmetipNcnL3ufIrZzK5fY2vaxgT8Jlhe2rBy7NdS98EVhmMxPQvFLaMrbXddEWgcXa5wdEXc4rayi7bTA==";

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "user@example.com";

    private JwtProperties properties;
    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        jwtService = new JwtService(properties);
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    @Test
    void generatedTokenCarriesIdentityClaims() {
        String token = jwtService.generateAccessToken(principal());

        Claims claims = jwtService.parseToken(token);
        assertEquals(EMAIL, claims.getSubject());
        assertEquals(USER_ID, jwtService.getUserIdFromToken(token));
        assertEquals(Set.of(SecurityConstants.ROLE_USER), jwtService.getRolesFromToken(token));
        assertEquals(SecurityConstants.TOKEN_TYPE_ACCESS,
                claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    @Test
    void generatedTokenIsSignedWithHs512() {
        String token = jwtService.generateAccessToken(principal());

        assertEquals("HS512", headerAlgorithmOf(token));
    }

    @Test
    void generatedTokenCarriesIssuerAndAudience() {
        Claims claims = jwtService.parseToken(jwtService.generateAccessToken(principal()));

        assertEquals("linkflow", claims.getIssuer());
        assertTrue(claims.getAudience().contains("linkflow-api"));
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(Base64.getEncoder().encodeToString(new byte[64])));
        String forged = Jwts.builder()
                .subject(EMAIL)
                .issuer("linkflow")
                .audience().add("linkflow-api").and()
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(attackerKey, Jwts.SIG.HS512)
                .compact();

        assertFalse(jwtService.validateToken(forged));
    }

    @Test
    void rejectsTokenFromAnotherIssuerEvenWhenSignedWithOurKey() {
        // Matters when a signing key is shared across services: a token minted elsewhere must not
        // grant access here.
        String foreign = signedToken(builder -> builder
                .issuer("some-other-service")
                .audience().add("linkflow-api").and());

        assertFalse(jwtService.validateToken(foreign));
    }

    @Test
    void rejectsTokenIntendedForAnotherAudience() {
        String foreign = signedToken(builder -> builder
                .issuer("linkflow")
                .audience().add("some-other-api").and());

        assertFalse(jwtService.validateToken(foreign));
    }

    @Test
    void rejectsTokenWithNoIssuerOrAudience() {
        String bare = signedToken(builder -> builder);

        assertFalse(jwtService.validateToken(bare));
    }

    @Test
    void rejectsUnsignedToken() {
        String unsigned = Jwts.builder()
                .subject(EMAIL)
                .issuer("linkflow")
                .audience().add("linkflow-api").and()
                .compact();

        assertFalse(jwtService.validateToken(unsigned));
    }

    @Test
    void rejectsExpiredToken() {
        properties.setAccessExpirationMs(-60_000);
        JwtService expiringService = new JwtService(properties);

        assertFalse(expiringService.validateToken(expiringService.generateAccessToken(principal())));
    }

    @Test
    void rejectsMalformedToken() {
        assertFalse(jwtService.validateToken("not.a.jwt"));
    }

    private String signedToken(java.util.function.UnaryOperator<io.jsonwebtoken.JwtBuilder> customizer) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(EMAIL)
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        return customizer.apply(builder).signWith(signingKey, Jwts.SIG.HS512).compact();
    }

    private static String headerAlgorithmOf(String token) {
        String headerJson = new String(Base64.getUrlDecoder()
                .decode(token.substring(0, token.indexOf('.'))));
        return headerJson.replaceAll(".*\"alg\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static UserPrincipal principal() {
        return new UserPrincipal(USER_ID, EMAIL, "hashed",
                Set.of(SecurityConstants.ROLE_USER), true);
    }
}
