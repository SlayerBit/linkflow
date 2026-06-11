package com.linkflow.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Paginated response wrapper for list endpoints.
 */
@Getter
@Builder
@AllArgsConstructor
public class PagedResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public static <T> PagedResponse<T> of(List<T> content, int page, int size,
                                           long totalElements, int totalPages) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
