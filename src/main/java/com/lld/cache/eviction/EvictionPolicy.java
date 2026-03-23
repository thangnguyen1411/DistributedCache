package com.lld.cache.eviction;

import java.util.Optional;

public interface EvictionPolicy<K> {

    void keyAccessed(K key);

    void keyAdded(K key);

    void keyRemoved(K key);

    Optional<K> evict();

    int size();
}
