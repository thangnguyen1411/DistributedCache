package com.lld.cache.replication;

import com.lld.cache.hash.ConsistentHashRing;
import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReplicationManager<K, V> {

    private final ConsistentHashRing hashRing;
    private final ReplicationStrategy<K, V> strategy;
    private final int replicationFactor;
    private final Map<String, CacheNode<K, V>> nodeMap;

    public ReplicationManager(ConsistentHashRing hashRing,
                              ReplicationStrategy<K, V> strategy,
                              int replicationFactor,
                              Map<String, CacheNode<K, V>> nodeMap) {
        this.hashRing = hashRing;
        this.strategy = strategy;
        this.replicationFactor = replicationFactor;
        this.nodeMap = nodeMap;
    }

    public void replicate(K key, CacheEntry<K, V> entry) {
        if (replicationFactor <= 1) {
            return;
        }
        List<CacheNode<K, V>> replicaNodes = getReplicaNodes(key);
        if (!replicaNodes.isEmpty()) {
            strategy.replicate(key, entry, replicaNodes);
        }
    }

    public void replicateDelete(K key) {
        if (replicationFactor <= 1) {
            return;
        }
        List<CacheNode<K, V>> replicaNodes = getReplicaNodes(key);
        if (!replicaNodes.isEmpty()) {
            strategy.replicateDelete(key, replicaNodes);
        }
    }

    private List<CacheNode<K, V>> getReplicaNodes(K key) {
        List<String> replicaNodeIds = hashRing.getReplicaNodesForKey(
                String.valueOf(key), replicationFactor - 1);
        List<CacheNode<K, V>> nodes = new ArrayList<>();
        for (String nodeId : replicaNodeIds) {
            CacheNode<K, V> node = nodeMap.get(nodeId);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    public ReplicationStrategy<K, V> getStrategy() {
        return strategy;
    }
}
