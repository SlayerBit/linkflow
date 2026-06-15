package com.linkflow.analytics.domain.repository.projection;

import java.time.LocalDate;

public interface ClickTrendProjection {
    LocalDate getClickDate();
    long getClickCount();
}
