package com.lld.cache.storage;

import com.lld.cache.model.CacheEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCacheStore<K, V> implements CacheStore<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<K, V>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<CacheEntry<K, V>> get(K key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(K key, CacheEntry<K, V> entry) {
        store.put(key, entry);
    }

    @Override
    public CacheEntry<K, V> remove(K key) {
        return store.remove(key);
    }

    @Override
    public boolean containsKey(K key) {
        return store.containsKey(key);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public Collection<K> keys() {
        return new ArrayList<>(store.keySet());
    }

    @Override
    public Collection<CacheEntry<K, V>> entries() {
        return new ArrayList<>(store.values());
    }
}
