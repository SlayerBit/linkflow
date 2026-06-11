package com.linkflow.url.api.controller;

import com.linkflow.url.application.service.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/r")
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Short URL redirect")
public class RedirectController {

    private final RedirectService redirectService;

    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {
        String targetUrl = redirectService.resolveRedirect(shortCode, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(targetUrl).toString())
                .build();
    }
}
