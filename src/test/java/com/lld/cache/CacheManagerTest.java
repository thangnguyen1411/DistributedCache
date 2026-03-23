package com.lld.cache;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.exception.NodeNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    private CacheManager<String, String> manager;

    @BeforeEach
    void setUp() {
        CacheManager.resetInstance();
        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(100)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(2)
                .replicationMode(ReplicationMode.SYNC)
                .defaultTtl(Duration.ofMinutes(10))
                .build();
        manager = CacheManager.getInstance(config);
    }

    @AfterEach
    void tearDown() {
        CacheManager.resetInstance();
    }

    @Test
    void putAndGetWithSingleNode() {
        manager.addNode("node-1");

        manager.put("key1", "value1");

        assertEquals(Optional.of("value1"), manager.get("key1"));
    }

    @Test
    void getReturnsEmptyForMissingKey() {
        manager.addNode("node-1");

        assertEquals(Optional.empty(), manager.get("missing"));
    }

    @Test
    void deleteRemovesKey() {
        manager.addNode("node-1");

        manager.put("key1", "value1");
        assertTrue(manager.delete("key1"));
        assertEquals(Optional.empty(), manager.get("key1"));
    }

    @Test
    void existsChecksPresence() {
        manager.addNode("node-1");

        manager.put("key1", "value1");
        assertTrue(manager.exists("key1"));
        assertFalse(manager.exists("missing"));
    }

    @Test
    void multipleNodesDistributeData() {
        manager.addNode("node-1");
        manager.addNode("node-2");
        manager.addNode("node-3");

        for (int i = 0; i < 20; i++) {
            manager.put("key-" + i, "value-" + i);
        }

        for (int i = 0; i < 20; i++) {
            assertEquals(Optional.of("value-" + i), manager.get("key-" + i));
        }
    }

    @Test
    void replicationMakesDataAvailableOnReplica() {
        manager.addNode("node-1");
        manager.addNode("node-2");

        manager.put("key1", "value1");

        // Data should be available (on primary or replica)
        assertEquals(Optional.of("value1"), manager.get("key1"));
    }

    @Test
    void removeNodeRedistributesData() {
        manager.addNode("node-1");
        manager.addNode("node-2");
        manager.addNode("node-3");

        for (int i = 0; i < 10; i++) {
            manager.put("key-" + i, "value-" + i);
        }

        manager.removeNode("node-2");

        // All data should still be accessible
        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.of("value-" + i), manager.get("key-" + i),
                    "key-" + i + " should survive node removal");
        }
    }

    @Test
    void removeNonexistentNodeThrows() {
        assertThrows(NodeNotFoundException.class, () -> manager.removeNode("ghost"));
    }

    @Test
    void getNodeIdsReturnsAllNodes() {
        manager.addNode("node-1");
        manager.addNode("node-2");

        assertEquals(2, manager.getNodeIds().size());
        assertTrue(manager.getNodeIds().contains("node-1"));
        assertTrue(manager.getNodeIds().contains("node-2"));
    }

    @Test
    void getReturnsEmptyWhenNoNodes() {
        assertEquals(Optional.empty(), manager.get("key1"));
    }

    @Test
    void putWithCustomTtl() throws InterruptedException {
        manager.addNode("node-1");

        manager.put("key1", "value1", Duration.ofMillis(100));

        assertEquals(Optional.of("value1"), manager.get("key1"));

        Thread.sleep(200);

        assertEquals(Optional.empty(), manager.get("key1"));
    }
}
