package com.pockt.infrastructure.web;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {
    public static <T> PageResponse<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new PageResponse<>(items, nextCursor, hasMore);
    }
}
