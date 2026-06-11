package com.linkflow.common.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskingConverterTest {

    private SensitiveDataMaskingConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataMaskingConverter();
    }

    @Test
    void masksPasswordInJson() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("body={\"password\":\"Secret123!\"}");
        String result = converter.convert(event);
        assertFalse(result.contains("Secret123!"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void masksBearerToken() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.abc.def");
        String result = converter.convert(event);
        assertFalse(result.contains("eyJhbGciOiJIUzUxMiJ9"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void masksRefreshTokenField() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("{\"refreshToken\":\"opaque-token-value-here\"}");
        String result = converter.convert(event);
        assertFalse(result.contains("opaque-token-value-here"));
    }

    @Test
    void leavesSafeMessagesUnchanged() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("User registered: email=user@example.com");
        assertEquals("User registered: email=user@example.com", converter.convert(event));
    }
}
