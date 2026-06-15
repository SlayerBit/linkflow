package com.linkflow.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ClickTrendResponse {
    private final String date;
    private final long clicks;
}
