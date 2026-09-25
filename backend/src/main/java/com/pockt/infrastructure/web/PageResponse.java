package com.pockt.infrastructure.web;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {}
