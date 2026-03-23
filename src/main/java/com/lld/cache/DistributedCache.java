package com.lld.cache;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.event.CacheEventListener;
import com.lld.cache.node.CacheNode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class DistributedCache<K, V> {

    private final CacheManager<K, V> manager;

    public DistributedCache(CacheConfig config) {
        this.manager = CacheManager.getInstance(config);
    }

    public Optional<V> get(K key) {
        return manager.get(key);
    }

    public void put(K key, V value) {
        manager.put(key, value);
    }

    public void put(K key, V value, Duration ttl) {
        manager.put(key, value, ttl);
    }

    public boolean delete(K key) {
        return manager.delete(key);
    }

    public boolean exists(K key) {
        return manager.exists(key);
    }

    public CacheNode<K, V> addNode(String nodeId) {
        return manager.addNode(nodeId);
    }

    public void removeNode(String nodeId) {
        manager.removeNode(nodeId);
    }

    public List<String> getNodeIds() {
        return manager.getNodeIds();
    }

    public void subscribe(CacheEventListener listener) {
        manager.addEventListener(listener);
    }

    public void unsubscribe(CacheEventListener listener) {
        manager.removeEventListener(listener);
    }

    public void shutdown() {
        CacheManager.resetInstance();
    }
}
