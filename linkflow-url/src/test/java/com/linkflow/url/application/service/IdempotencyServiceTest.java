package com.linkflow.url.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkflow.url.api.dto.UrlResponse;
import com.linkflow.url.domain.entity.IdempotencyRecord;
import com.linkflow.url.domain.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(repository, objectMapper);
    }

    @Test
    void findCached_returnsStoredResponseWhenNotExpired() throws Exception {
        UUID userId = UUID.randomUUID();
        String responseJson = """
                {"shortCode":"abc1234","originalUrl":"https://example.com","active":true}
                """;

        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(userId)
                .endpoint("/api/v1/urls")
                .idempotencyKey("key-1")
                .responseStatus(201)
                .responseBody(responseJson)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(repository.findByUserIdAndEndpointAndIdempotencyKey(userId, "/api/v1/urls", "key-1"))
                .thenReturn(Optional.of(record));

        var result = idempotencyService.findCached(
                userId, "/api/v1/urls", "key-1", java.util.Map.class);

        assertTrue(result.isPresent());
        assertEquals(201, result.get().statusCode());
        assertEquals("abc1234", result.get().body().get("shortCode"));
    }

    @Test
    void findCached_ignoresExpiredRecord() {
        UUID userId = UUID.randomUUID();
        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(userId)
                .endpoint("/api/v1/urls")
                .idempotencyKey("key-expired")
                .responseStatus(201)
                .responseBody("{}")
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(repository.findByUserIdAndEndpointAndIdempotencyKey(userId, "/api/v1/urls", "key-expired"))
                .thenReturn(Optional.of(record));

        assertTrue(idempotencyService.findCached(userId, "/api/v1/urls", "key-expired", UrlResponse.class).isEmpty());
    }

    @Test
    void store_persistsRecordWith24HourExpiry() {
        UUID userId = UUID.randomUUID();
        UrlResponse body = UrlResponse.builder()
                .shortCode("xyz")
                .originalUrl("https://example.com")
                .build();

        idempotencyService.store(userId, "/api/v1/urls", "store-key", 201, body);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        IdempotencyRecord saved = captor.getValue();
        assertEquals("store-key", saved.getIdempotencyKey());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plus(23, ChronoUnit.HOURS)));
    }
}
