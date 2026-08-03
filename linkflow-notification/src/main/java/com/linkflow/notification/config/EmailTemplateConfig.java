package com.linkflow.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

@Configuration
public class EmailTemplateConfig {

    /**
     * Standalone engine for email bodies, deliberately separate from any MVC view resolution.
     * linkflow-app serves JSON only, so registering a Spring-integrated view resolver there would
     * add an MVC concern the application does not want.
     */
    @Bean
    public TemplateEngine emailTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /**
     * Dedicated pool so a slow or unreachable SMTP relay cannot starve click tracking, which
     * shares no threads with mail delivery.
     * <p>
     * The queue is bounded and rejection is left at the default abort policy, which is the right
     * choice here even though it means a rejected message is not sent. CallerRuns is the tempting
     * alternative, but sends are dispatched from an after-commit listener that runs on the request
     * thread: absorbing the work there would block an HTTP response behind an SMTP conversation
     * that is already retrying against an unresponsive relay, for up to a minute. Registration
     * would hang precisely when mail is most broken.
     * <p>
     * Aborting is survivable because every affected flow has a user-driven retry — resend
     * verification, request another reset link — and the rejection is logged loudly by the
     * dispatcher rather than disappearing. Reaching this point at all means 200 queued messages
     * and four stuck threads, which is an outage to be alerted on, not a case to be absorbed
     * quietly.
     * <p>
     * Draining on shutdown is enabled so a deploy does not discard messages already queued.
     */
    @Bean("emailExecutor")
    public TaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
