package com.lld.cache.model;

import java.time.Instant;

public record CacheEvent(
        CacheEventType type,
        String key,
        String nodeId,
        Instant timestamp
) {
    public CacheEvent(CacheEventType type, String key, String nodeId) {
        this(type, key, nodeId, Instant.now());
    }
}
