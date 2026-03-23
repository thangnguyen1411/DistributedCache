package com.lld.cache.replication;

import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;

import java.util.List;

public interface ReplicationStrategy<K, V> {

    void replicate(K key, CacheEntry<K, V> entry, List<CacheNode<K, V>> replicaNodes);

    void replicateDelete(K key, List<CacheNode<K, V>> replicaNodes);
}
