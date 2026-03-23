package com.lld.cache.expiration;

import com.lld.cache.model.CacheEntry;

import java.time.Instant;

public class LazyExpirationChecker<K, V> {

    public boolean isExpired(CacheEntry<K, V> entry) {
        return entry != null && entry.isExpired(Instant.now());
    }
}
