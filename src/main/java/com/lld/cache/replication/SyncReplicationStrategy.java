package com.lld.cache.replication;

import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;

import java.util.List;

public class SyncReplicationStrategy<K, V> implements ReplicationStrategy<K, V> {

    @Override
    public void replicate(K key, CacheEntry<K, V> entry, List<CacheNode<K, V>> replicaNodes) {
        for (CacheNode<K, V> node : replicaNodes) {
            node.putEntry(key, entry);
        }
    }

    @Override
    public void replicateDelete(K key, List<CacheNode<K, V>> replicaNodes) {
        for (CacheNode<K, V> node : replicaNodes) {
            node.delete(key);
        }
    }
}
