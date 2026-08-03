package com.linkflow.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "clickTrackingExecutor")
    public Executor clickTrackingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("click-track-");

        // Drain queued click writes on shutdown rather than discarding them. Without this, a
        // rolling deploy silently loses whatever analytics were still queued.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Bounded so a stuck task cannot hold the shutdown open past the graceful window.
        executor.setAwaitTerminationSeconds(15);

        // The default abort policy is deliberate. Saturation here means Redis is struggling, and
        // the alternative — running the task on the caller — would put that latency directly into
        // redirects. Losing analytics is recoverable; making every redirect slow is not. The
        // caller catches the rejection and logs it.
        executor.initialize();
        return executor;
    }
}
