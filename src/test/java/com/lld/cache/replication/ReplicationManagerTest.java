package com.lld.cache.replication;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.event.CacheEventBus;
import com.lld.cache.hash.ConsistentHashRing;
import com.lld.cache.model.CacheEntry;
import com.lld.cache.node.CacheNode;
import com.lld.cache.node.CacheNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ReplicationManagerTest {

    private ConsistentHashRing hashRing;
    private Map<String, CacheNode<String, String>> nodeMap;
    private ReplicationManager<String, String> replicationManager;
    private CacheEventBus eventBus;
    private CacheConfig config;

    @BeforeEach
    void setUp() {
        hashRing = new ConsistentHashRing(50);
        nodeMap = new ConcurrentHashMap<>();
        eventBus = new CacheEventBus();
        config = new CacheConfig.Builder()
                .maxEntriesPerNode(100)
                .replicationFactor(2)
                .build();

        SyncReplicationStrategy<String, String> strategy = new SyncReplicationStrategy<>();
        replicationManager = new ReplicationManager<>(hashRing, strategy, 2, nodeMap);

        // Add 3 nodes
        for (int i = 1; i <= 3; i++) {
            String nodeId = "node-" + i;
            CacheNode<String, String> node = CacheNodeFactory.createNode(nodeId, config, eventBus);
            nodeMap.put(nodeId, node);
            hashRing.addNode(nodeId);
        }
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    @Test
    void replicateWritesToReplicaNode() {
        CacheEntry<String, String> entry = new CacheEntry<>("key1", "value1");

        // Put to primary
        String primaryId = hashRing.getNodeForKey("key1");
        nodeMap.get(primaryId).put("key1", "value1");

        // Replicate
        replicationManager.replicate("key1", entry);

        // Verify at least one replica has the data
        int nodesWithData = 0;
        for (CacheNode<String, String> node : nodeMap.values()) {
            if (node.exists("key1")) {
                nodesWithData++;
            }
        }
        assertTrue(nodesWithData >= 2, "Data should exist on primary + at least 1 replica");
    }

    @Test
    void replicateDeleteRemovesFromReplicas() {
        CacheEntry<String, String> entry = new CacheEntry<>("key1", "value1");

        String primaryId = hashRing.getNodeForKey("key1");
        nodeMap.get(primaryId).put("key1", "value1");
        replicationManager.replicate("key1", entry);

        // Delete from primary and replicate
        nodeMap.get(primaryId).delete("key1");
        replicationManager.replicateDelete("key1");

        // Verify no node has the data
        for (CacheNode<String, String> node : nodeMap.values()) {
            assertFalse(node.exists("key1"), "Key should be deleted from all nodes");
        }
    }

    @Test
    void noReplicationWhenFactorIsOne() {
        ReplicationManager<String, String> noRepl = new ReplicationManager<>(
                hashRing, new SyncReplicationStrategy<>(), 1, nodeMap);

        CacheEntry<String, String> entry = new CacheEntry<>("key1", "value1");
        String primaryId = hashRing.getNodeForKey("key1");
        nodeMap.get(primaryId).put("key1", "value1");

        noRepl.replicate("key1", entry);

        // Only primary should have it
        int count = 0;
        for (CacheNode<String, String> node : nodeMap.values()) {
            if (node.exists("key1")) count++;
        }
        assertEquals(1, count);
    }
}
