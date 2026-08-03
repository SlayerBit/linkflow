package com.linkflow.url.domain.event;

/**
 * Raised when a short URL's redirect-relevant state changes and its cached copy is no longer valid.
 *
 * @param shortCode the affected short code
 */
public record UrlMutatedEvent(String shortCode) {
}
