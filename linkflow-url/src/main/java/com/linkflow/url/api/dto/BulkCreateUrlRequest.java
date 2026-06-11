package com.linkflow.url.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateUrlRequest {

    @NotEmpty(message = "At least one URL is required")
    @Valid
    private List<CreateUrlRequest> urls;
}
