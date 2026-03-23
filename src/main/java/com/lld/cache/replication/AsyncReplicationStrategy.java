package com.lld.cache.replication;

import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncReplicationStrategy<K, V> implements ReplicationStrategy<K, V> {

    private final ExecutorService executor;

    public AsyncReplicationStrategy(int threadPoolSize) {
        this.executor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "cache-replication");
            t.setDaemon(true);
            return t;
        });
    }

    public AsyncReplicationStrategy() {
        this(2);
    }

    @Override
    public void replicate(K key, CacheEntry<K, V> entry, List<CacheNode<K, V>> replicaNodes) {
        for (CacheNode<K, V> node : replicaNodes) {
            executor.submit(() -> node.putEntry(key, entry));
        }
    }

    @Override
    public void replicateDelete(K key, List<CacheNode<K, V>> replicaNodes) {
        for (CacheNode<K, V> node : replicaNodes) {
            executor.submit(() -> node.delete(key));
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
