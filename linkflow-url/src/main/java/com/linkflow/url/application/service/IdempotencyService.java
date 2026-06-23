package com.linkflow.url.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.url.domain.entity.IdempotencyRecord;
import com.linkflow.url.domain.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final long EXPIRY_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public <T> Optional<CachedIdempotentResult<T>> findCached(
            UUID userId, String endpoint, String idempotencyKey, String requestBodyHash, Class<T> responseType) {
        return idempotencyRecordRepository
                .findByUserIdAndEndpointAndIdempotencyKey(userId, endpoint, idempotencyKey)
                .filter(record -> record.getExpiresAt().isAfter(Instant.now()))
                .map(record -> validateAndDeserialize(record, requestBodyHash, responseType));
    }

    @Transactional
    public void store(UUID userId, String endpoint, String idempotencyKey,
                      String requestBodyHash, int statusCode, Object responseBody) {
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .userId(userId)
                    .endpoint(endpoint)
                    .idempotencyKey(idempotencyKey)
                    .requestBodyHash(requestBodyHash)
                    .responseStatus(statusCode)
                    .responseBody(objectMapper.writeValueAsString(responseBody))
                    .expiresAt(Instant.now().plus(EXPIRY_HOURS, ChronoUnit.HOURS))
                    .build();
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException ex) {
            log.error("Failed to store idempotency record for userId={}, endpoint={}", userId, endpoint, ex);
            throw new IllegalStateException("Failed to store idempotency record", ex);
        }
    }

    public String hashRequestBody(Object requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash request body", ex);
        }
    }

    private <T> CachedIdempotentResult<T> validateAndDeserialize(
            IdempotencyRecord record, String requestBodyHash, Class<T> responseType) {
        if (StringUtils.hasText(record.getRequestBodyHash())
                && StringUtils.hasText(requestBodyHash)
                && !record.getRequestBodyHash().equals(requestBodyHash)) {
            throw new ConflictException(
                    "Idempotency key was already used with a different request body");
        }
        return deserialize(record, responseType);
    }

    private <T> CachedIdempotentResult<T> deserialize(IdempotencyRecord record, Class<T> responseType) {
        try {
            T body = objectMapper.readValue(record.getResponseBody(), responseType);
            return new CachedIdempotentResult<>(record.getResponseStatus(), body);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize idempotency record id={}", record.getId(), ex);
            throw new IllegalStateException("Failed to deserialize idempotency record", ex);
        }
    }

    public record CachedIdempotentResult<T>(int statusCode, T body) {}
}
