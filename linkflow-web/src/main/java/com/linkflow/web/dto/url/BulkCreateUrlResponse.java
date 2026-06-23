package com.linkflow.web.dto.url;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BulkCreateUrlResponse(
        List<UrlResponse> urls,
        int count
) {
}
