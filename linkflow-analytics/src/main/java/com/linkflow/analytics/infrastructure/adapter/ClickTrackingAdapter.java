package com.linkflow.analytics.infrastructure.adapter;

import com.linkflow.analytics.application.service.ClickTrackingService;
import com.linkflow.common.port.ClickTrackingPort;
import com.linkflow.common.port.ClickTrackingPort.ClickTrackingCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClickTrackingAdapter implements ClickTrackingPort {

    private final ClickTrackingService clickTrackingService;

    @Override
    public void trackClick(ClickTrackingCommand command) {
        clickTrackingService.trackClick(command);
    }
}
