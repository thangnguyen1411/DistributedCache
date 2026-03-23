package com.lld.cache.expiration;

import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpirationManager<K, V> {

    private final ScheduledExecutorService scheduler;
    private final List<CacheNode<K, V>> nodes = new CopyOnWriteArrayList<>();
    private final Duration cleanupInterval;

    public ExpirationManager(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-expiration-cleaner");
            t.setDaemon(true);
            return t;
        });
    }

    public void addNode(CacheNode<K, V> node) {
        nodes.add(node);
    }

    public void removeNode(CacheNode<K, V> node) {
        nodes.remove(node);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries,
                cleanupInterval.toMillis(),
                cleanupInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        for (CacheNode<K, V> node : nodes) {
            Map<K, CacheEntry<K, V>> entries = node.getAllEntries();
            for (Map.Entry<K, CacheEntry<K, V>> entry : entries.entrySet()) {
                if (entry.getValue().isExpired(now)) {
                    node.delete(entry.getKey());
                }
            }
        }
    }
}
