package com.lld.cache.storage;

import com.lld.cache.model.CacheEntry;

import java.util.Collection;
import java.util.Optional;

public interface CacheStore<K, V> {

    Optional<CacheEntry<K, V>> get(K key);

    void put(K key, CacheEntry<K, V> entry);

    CacheEntry<K, V> remove(K key);

    boolean containsKey(K key);

    int size();

    void clear();

    Collection<K> keys();

    Collection<CacheEntry<K, V>> entries();
}
