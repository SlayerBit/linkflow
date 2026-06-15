package com.linkflow.web.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickTrendResponse {
    private String date;
    private long clicks;
}
