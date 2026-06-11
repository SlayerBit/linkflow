package com.linkflow.url.api.controller;

import com.linkflow.common.api.ApiResponse;
import com.linkflow.common.api.PagedResponse;
import com.linkflow.url.api.dto.*;
import com.linkflow.url.application.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
@Tag(name = "URL", description = "Short URL management")
public class UrlController {

    private final UrlService urlService;

    @PostMapping
    @Operation(summary = "Create a short URL")
    public ResponseEntity<ApiResponse<UrlResponse>> createUrl(
            @Valid @RequestBody CreateUrlRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UrlResponse response = urlService.createUrl(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create short URLs")
    public ResponseEntity<ApiResponse<BulkCreateUrlResponse>> bulkCreateUrls(
            @Valid @RequestBody BulkCreateUrlRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        BulkCreateUrlResponse response = urlService.bulkCreateUrls(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    @Operation(summary = "List current user's short URLs")
    public ResponseEntity<ApiResponse<PagedResponse<UrlResponse>>> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        size = Math.min(size, 100);
        Sort sort = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<UrlResponse> urls = urlService.listUserUrls(pageable);
        PagedResponse<UrlResponse> pagedResponse = PagedResponse.of(
                urls.getContent(), urls.getNumber(), urls.getSize(),
                urls.getTotalElements(), urls.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.of(pagedResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get short URL details")
    public ResponseEntity<ApiResponse<UrlResponse>> getUrl(@PathVariable UUID id) {
        UrlResponse response = urlService.getUrlById(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update short URL")
    public ResponseEntity<ApiResponse<UrlResponse>> updateUrl(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUrlRequest request) {
        UrlResponse response = urlService.updateUrl(id, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete short URL")
    public ResponseEntity<ApiResponse<Void>> deleteUrl(@PathVariable UUID id) {
        urlService.deleteUrl(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get QR code for short URL")
    public ResponseEntity<byte[]> getQrCode(@PathVariable UUID id) {
        byte[] png = urlService.generateQrCode(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
