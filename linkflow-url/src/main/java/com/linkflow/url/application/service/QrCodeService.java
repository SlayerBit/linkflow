package com.linkflow.url.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.linkflow.url.infrastructure.config.UrlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private static final int QR_SIZE = 250;

    private final UrlProperties urlProperties;

    private final Cache<String, byte[]> qrCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public byte[] generatePng(String shortCode) {
        String normalized = shortCode.toLowerCase();
        return qrCache.get(normalized, this::generatePngUncached);
    }

    public void evict(String shortCode) {
        qrCache.invalidate(shortCode.toLowerCase());
    }

    private byte[] generatePngUncached(String shortCode) {
        String targetUrl = buildShortUrl(shortCode);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(targetUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate QR code for shortCode={}", shortCode, ex);
            throw new IllegalStateException("Failed to generate QR code", ex);
        }
    }

    private String buildShortUrl(String shortCode) {
        String baseUrl = urlProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/r/" + shortCode;
    }
}
