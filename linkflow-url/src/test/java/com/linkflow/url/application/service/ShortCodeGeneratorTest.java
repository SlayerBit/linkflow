package com.linkflow.url.application.service;

import com.linkflow.url.domain.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortCodeGeneratorTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    private ShortCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ShortCodeGenerator(shortUrlRepository);
    }

    @Test
    void generate_returnsSevenCharCode() {
        when(shortUrlRepository.existsByShortCode(anyString())).thenReturn(false);
        String code = generator.generate();
        assertNotNull(code);
        assertEquals(7, code.length());
        assertTrue(code.matches("^[0-9a-zA-Z]+$"));
    }
}
