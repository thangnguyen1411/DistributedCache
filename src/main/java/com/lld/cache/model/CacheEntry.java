package com.lld.cache.model;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CacheEntry<K, V> {

    private final K key;
    private final V value;
    private final Instant createdAt;
    private final Duration ttl;
    private final AtomicReference<Instant> lastAccessedAt;
    private final AtomicInteger accessCount;

    public CacheEntry(K key, V value, Duration ttl) {
        this.key = key;
        this.value = value;
        this.createdAt = Instant.now();
        this.ttl = ttl;
        this.lastAccessedAt = new AtomicReference<>(this.createdAt);
        this.accessCount = new AtomicInteger(0);
    }

    public CacheEntry(K key, V value) {
        this(key, value, null);
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Duration getTtl() {
        return ttl;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt.get();
    }

    public int getAccessCount() {
        return accessCount.get();
    }

    public void recordAccess() {
        lastAccessedAt.set(Instant.now());
        accessCount.incrementAndGet();
    }

    public boolean isExpired() {
        if (ttl == null) {
            return false;
        }
        return Instant.now().isAfter(createdAt.plus(ttl));
    }

    public boolean isExpired(Instant now) {
        if (ttl == null) {
            return false;
        }
        return now.isAfter(createdAt.plus(ttl));
    }

    public Duration remainingTtl() {
        if (ttl == null) {
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), createdAt.plus(ttl));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
