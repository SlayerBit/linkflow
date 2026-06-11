package com.linkflow.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive values in log messages (passwords, tokens, secrets).
 */
public class SensitiveDataMaskingConverter extends MessageConverter {

    private static final String REDACTED = "[REDACTED]";

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?i)(\"password\"\\s*:\\s*\")([^\"]+)(\")"),
            Pattern.compile("(?i)(\"refreshToken\"\\s*:\\s*\")([^\"]+)(\")"),
            Pattern.compile("(?i)(\"accessToken\"\\s*:\\s*\")([^\"]+)(\")"),
            Pattern.compile("(?i)(\"password_hash\"\\s*:\\s*\")([^\"]+)(\")"),
            Pattern.compile("(?i)(\"passwordHash\"\\s*:\\s*\")([^\"]+)(\")"),
            Pattern.compile("(?i)(Bearer\\s+)(\\S+)"),
            Pattern.compile("(eyJ[A-Za-z0-9_-]*\\.eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*)"),
            Pattern.compile("(?i)(LINKFLOW_JWT_SECRET=)(\\S+)"),
            Pattern.compile("(?i)(secret=)(\\S+)")
    };

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isBlank()) {
            return message;
        }
        String masked = message;
        for (Pattern pattern : PATTERNS) {
            masked = maskPattern(masked, pattern);
        }
        return masked;
    }

    private String maskPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            if (matcher.groupCount() >= 3) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(matcher.group(1) + REDACTED + matcher.group(3)));
            } else if (matcher.groupCount() >= 2) {
                matcher.appendReplacement(result,
                        Matcher.quoteReplacement(matcher.group(1) + REDACTED));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(REDACTED));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
