package com.lld.cache.node;

import com.lld.cache.model.CacheEntry;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface CacheNode<K, V> {

    String getNodeId();

    CacheNodeStatus getStatus();

    Optional<V> get(K key);

    void put(K key, V value);

    void put(K key, V value, Duration ttl);

    boolean delete(K key);

    boolean exists(K key);

    Optional<Duration> ttl(K key);

    int size();

    int capacity();

    boolean isFull();

    void clear();

    Map<K, CacheEntry<K, V>> getAllEntries();

    void putEntry(K key, CacheEntry<K, V> entry);
}
