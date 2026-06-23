package com.linkflow.web.session;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;

import java.io.*;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AuthStateSerializationTest {

    @Test
    void testAuthStateSerialization() throws IOException, ClassNotFoundException {
        AuthState authState = new AuthState(
                "access-token-123",
                "refresh-token-456",
                System.currentTimeMillis() / 1000 + 3600,
                "user@example.com",
                "John",
                "Doe",
                Set.of("USER", "ADMIN")
        );

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(authState);
        }

        byte[] serializedData = baos.toByteArray();
        assertTrue(serializedData.length > 0, "Serialized data should not be empty");

        // Deserialize
        AuthState deserialized;
        ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (AuthState) ois.readObject();
        }

        // Assertions
        assertNotNull(deserialized);
        assertEquals(authState.accessToken(), deserialized.accessToken());
        assertEquals(authState.refreshToken(), deserialized.refreshToken());
        assertEquals(authState.expiresAt(), deserialized.expiresAt());
        assertEquals(authState.email(), deserialized.email());
        assertEquals(authState.firstName(), deserialized.firstName());
        assertEquals(authState.lastName(), deserialized.lastName());
        assertEquals(authState.roles(), deserialized.roles());
        assertTrue(deserialized.isAdmin());
        assertEquals("John Doe", deserialized.displayName());
    }

    @Test
    void testSecurityContextSerialization() throws IOException, ClassNotFoundException {
        Set<String> roles = Set.of("USER", "ADMIN");
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        var authentication = new UsernamePasswordAuthenticationToken("user@example.com", null, authorities);
        var securityContext = new SecurityContextImpl(authentication);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(securityContext);
        }

        byte[] serializedData = baos.toByteArray();
        assertTrue(serializedData.length > 0, "Serialized data should not be empty");

        // Deserialize
        SecurityContextImpl deserialized;
        ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (SecurityContextImpl) ois.readObject();
        }

        // Assertions
        assertNotNull(deserialized);
        assertNotNull(deserialized.getAuthentication());
        assertEquals("user@example.com", deserialized.getAuthentication().getName());
        assertEquals(2, deserialized.getAuthentication().getAuthorities().size());
    }
}
