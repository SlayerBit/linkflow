package com.linkflow.url.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class BulkCreateUrlResponse {

    private final List<UrlResponse> urls;
    private final int count;
}
