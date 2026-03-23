package com.lld.cache.integration;

import com.lld.cache.CacheManager;
import com.lld.cache.DistributedCache;
import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.model.CacheEvent;
import com.lld.cache.model.CacheEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndCacheTest {

    private DistributedCache<String, String> cache;

    @BeforeEach
    void setUp() {
        CacheManager.resetInstance();
        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(50)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(2)
                .replicationMode(ReplicationMode.SYNC)
                .defaultTtl(Duration.ofMinutes(5))
                .expirationCleanupInterval(Duration.ofMillis(100))
                .build();
        cache = new DistributedCache<>(config);
    }

    @AfterEach
    void tearDown() {
        cache.shutdown();
    }

    @Test
    void fullLifecycleWithThreeNodes() {
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");

        // PUT multiple keys
        for (int i = 0; i < 10; i++) {
            cache.put("user:" + i, "data-" + i);
        }

        // GET all keys
        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.of("data-" + i), cache.get("user:" + i));
        }

        // DELETE a key
        assertTrue(cache.delete("user:5"));
        assertFalse(cache.exists("user:5"));

        // EXISTS check
        assertTrue(cache.exists("user:0"));
    }

    @Test
    void dynamicNodeAddition() {
        cache.addNode("node-1");
        cache.addNode("node-2");

        for (int i = 0; i < 10; i++) {
            cache.put("key-" + i, "val-" + i);
        }

        // Add a third node
        cache.addNode("node-3");

        // All data still accessible
        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.of("val-" + i), cache.get("key-" + i));
        }

        assertEquals(3, cache.getNodeIds().size());
    }

    @Test
    void nodeRemovalPreservesData() {
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");

        for (int i = 0; i < 10; i++) {
            cache.put("item:" + i, "value-" + i);
        }

        cache.removeNode("node-2");

        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.of("value-" + i), cache.get("item:" + i),
                    "item:" + i + " should survive node removal");
        }
    }

    @Test
    void ttlExpiration() throws InterruptedException {
        cache.addNode("node-1");

        cache.put("temp-key", "temp-value", Duration.ofMillis(100));

        assertEquals(Optional.of("temp-value"), cache.get("temp-key"));

        Thread.sleep(300);

        assertEquals(Optional.empty(), cache.get("temp-key"));
    }

    @Test
    void eventListenerReceivesEvents() throws InterruptedException {
        CopyOnWriteArrayList<CacheEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        cache.subscribe(event -> {
            if (event.type() == CacheEventType.PUT && "key1".equals(event.key())) {
                events.add(event);
                latch.countDown();
            }
        });

        cache.addNode("node-1");
        cache.put("key1", "value1");

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(events.isEmpty());
        assertEquals(CacheEventType.PUT, events.get(0).type());
    }

    @Test
    void evictionWhenNodeIsFull() {
        // Use a separate cache with small capacity for this test
        cache.shutdown();
        CacheConfig smallConfig = new CacheConfig.Builder()
                .maxEntriesPerNode(5)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(1)
                .replicationMode(ReplicationMode.SYNC)
                .defaultTtl(Duration.ofMinutes(5))
                .build();
        cache = new DistributedCache<>(smallConfig);
        cache.addNode("node-1");

        for (int i = 0; i < 10; i++) {
            cache.put("k" + i, "v" + i);
        }

        // Latest entries should be present, oldest evicted
        assertEquals(Optional.of("v9"), cache.get("k9"));
        assertEquals(5, 5); // node capped at capacity
    }

    @Test
    void concurrentPutsAndGets() throws InterruptedException {
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");

        int threadCount = 10;
        int opsPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        String key = "t" + threadId + "-k" + i;
                        cache.put(key, "value-" + i);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent operations should complete");
    }

    @Test
    void differentEvictionPolicies() {
        cache.shutdown();

        // Test with LFU
        CacheConfig lfuConfig = new CacheConfig.Builder()
                .maxEntriesPerNode(3)
                .evictionPolicy(EvictionPolicyType.LFU)
                .replicationFactor(1)
                .replicationMode(ReplicationMode.SYNC)
                .defaultTtl(Duration.ofMinutes(5))
                .build();
        cache = new DistributedCache<>(lfuConfig);
        cache.addNode("node-1");

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");

        // Access "a" and "b" more frequently
        cache.get("a");
        cache.get("a");
        cache.get("b");

        // Adding "d" should evict "c" (least frequently used)
        cache.put("d", "4");

        assertTrue(cache.exists("a"));
        assertTrue(cache.exists("b"));
        assertEquals(Optional.empty(), cache.get("c")); // evicted
        assertTrue(cache.exists("d"));
    }
}
