package com.linkflow.url.application.service;

import com.linkflow.common.util.Base62;
import com.linkflow.url.domain.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ShortCodeGenerator {

    private static final int SHORT_CODE_LENGTH = 7;
    private static final int MAX_RETRIES = 10;

    private final ShortUrlRepository shortUrlRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String candidate = generateCandidate();
            if (!shortUrlRepository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code after " + MAX_RETRIES + " attempts");
    }

    private String generateCandidate() {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < SHORT_CODE_LENGTH) {
            long value = Math.abs(UUID.randomUUID().getMostSignificantBits()
                    ^ ThreadLocalRandom.current().nextLong());
            builder.append(Base62.encode(value));
        }
        return builder.substring(0, SHORT_CODE_LENGTH).toLowerCase();
    }
}
