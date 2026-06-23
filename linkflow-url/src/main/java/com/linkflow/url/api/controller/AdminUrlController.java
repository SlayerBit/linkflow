package com.linkflow.url.api.controller;

import com.linkflow.common.api.ApiResponse;
import com.linkflow.common.api.PagedResponse;
import com.linkflow.url.api.dto.UrlResponse;
import com.linkflow.url.application.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/urls")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - URLs", description = "Admin short URL management")
public class AdminUrlController {

    private final UrlService urlService;

    @GetMapping
    @Operation(summary = "List all short URLs (admin)")
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

        Page<UrlResponse> urls = urlService.listAllUrls(pageable);
        PagedResponse<UrlResponse> pagedResponse = PagedResponse.of(
                urls.getContent(), urls.getNumber(), urls.getSize(),
                urls.getTotalElements(), urls.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.of(pagedResponse));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate short URL (admin)")
    public ResponseEntity<ApiResponse<UrlResponse>> deactivateUrl(@PathVariable UUID id) {
        UrlResponse response = urlService.adminDeactivateUrl(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PatchMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate short URL (admin)")
    public ResponseEntity<ApiResponse<UrlResponse>> reactivateUrl(@PathVariable UUID id) {
        UrlResponse response = urlService.adminReactivateUrl(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get short URL by ID (admin)")
    public ResponseEntity<ApiResponse<UrlResponse>> getUrlById(@PathVariable UUID id) {
        UrlResponse response = urlService.getUrlByIdAsAdmin(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get QR code PNG for any URL (admin)")
    public ResponseEntity<byte[]> getQrCode(@PathVariable UUID id) {
        byte[] png = urlService.generateQrCodeAsAdmin(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
