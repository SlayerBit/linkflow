package com.linkflow.url.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUrlRequest {

    private Instant expiresAt;
    private Boolean active;
}
