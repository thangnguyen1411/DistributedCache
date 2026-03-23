package com.lld.cache.hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ConsistentHashRing {

    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodeCount;
    private final HashFunction hashFunction;

    public ConsistentHashRing(int virtualNodeCount, HashFunction hashFunction) {
        this.virtualNodeCount = virtualNodeCount;
        this.hashFunction = hashFunction;
    }

    public ConsistentHashRing(int virtualNodeCount) {
        this(virtualNodeCount, new MD5HashFunction());
    }

    public void addNode(String nodeId) {
        for (int i = 0; i < virtualNodeCount; i++) {
            long hash = hashFunction.hash(nodeId + "#" + i);
            ring.put(hash, nodeId);
        }
    }

    public void removeNode(String nodeId) {
        for (int i = 0; i < virtualNodeCount; i++) {
            long hash = hashFunction.hash(nodeId + "#" + i);
            ring.remove(hash);
        }
    }

    public String getNodeForKey(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = hashFunction.hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    public List<String> getReplicaNodesForKey(String key, int replicaCount) {
        if (ring.isEmpty()) {
            return Collections.emptyList();
        }

        long hash = hashFunction.hash(key);
        List<String> replicas = new ArrayList<>();
        String primaryNode = getNodeForKey(key);

        // Walk clockwise from primary position
        Map.Entry<Long, String> entry = ring.higherEntry(hash);
        // Wrap around and iterate
        TreeMap<Long, String> tailMap = entry != null
                ? new TreeMap<>(ring.tailMap(entry.getKey(), true))
                : new TreeMap<>();
        TreeMap<Long, String> headMap = new TreeMap<>(ring.headMap(hash, true));

        // Combine tail + head for full clockwise traversal
        List<Map.Entry<Long, String>> clockwise = new ArrayList<>(tailMap.entrySet());
        clockwise.addAll(headMap.entrySet());

        for (Map.Entry<Long, String> e : clockwise) {
            String nodeId = e.getValue();
            if (!nodeId.equals(primaryNode) && !replicas.contains(nodeId)) {
                replicas.add(nodeId);
                if (replicas.size() >= replicaCount) {
                    break;
                }
            }
        }
        return replicas;
    }

    public boolean isEmpty() {
        return ring.isEmpty();
    }

    public int size() {
        return ring.size();
    }

    public List<String> getDistinctNodes() {
        return new ArrayList<>(new java.util.LinkedHashSet<>(ring.values()));
    }
}
