package com.lld.cache.node;

import com.lld.cache.eviction.LRUEvictionPolicy;
import com.lld.cache.event.CacheEventBus;
import com.lld.cache.expiration.LazyExpirationChecker;
import com.lld.cache.model.CacheEntry;
import com.lld.cache.storage.InMemoryCacheStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheNodeImplTest {

    private CacheNodeImpl<String, String> node;
    private CacheEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new CacheEventBus();
        node = new CacheNodeImpl<>(
                "test-node", 3,
                new InMemoryCacheStore<>(),
                new LRUEvictionPolicy<>(),
                new LazyExpirationChecker<>(),
                eventBus
        );
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    @Test
    void putAndGet() {
        node.put("key1", "value1");

        assertEquals(Optional.of("value1"), node.get("key1"));
    }

    @Test
    void getMissingKeyReturnsEmpty() {
        assertEquals(Optional.empty(), node.get("missing"));
    }

    @Test
    void deleteRemovesKey() {
        node.put("key1", "value1");

        assertTrue(node.delete("key1"));
        assertEquals(Optional.empty(), node.get("key1"));
    }

    @Test
    void deleteNonexistentKeyReturnsFalse() {
        assertFalse(node.delete("missing"));
    }

    @Test
    void existsReturnsCorrectly() {
        node.put("key1", "value1");

        assertTrue(node.exists("key1"));
        assertFalse(node.exists("missing"));
    }

    @Test
    void evictsWhenFull() {
        node.put("a", "1");
        node.put("b", "2");
        node.put("c", "3");

        // Node is at capacity (3), adding new key evicts LRU
        node.put("d", "4");

        assertEquals(3, node.size());
        assertEquals(Optional.empty(), node.get("a")); // evicted (LRU)
        assertEquals(Optional.of("4"), node.get("d"));
    }

    @Test
    void updateExistingKeyDoesNotEvict() {
        node.put("a", "1");
        node.put("b", "2");
        node.put("c", "3");

        node.put("a", "updated"); // update, not new

        assertEquals(3, node.size());
        assertEquals(Optional.of("updated"), node.get("a"));
    }

    @Test
    void ttlExpiresEntry() throws InterruptedException {
        node.put("key1", "value1", Duration.ofMillis(50));

        assertEquals(Optional.of("value1"), node.get("key1"));

        Thread.sleep(100);

        assertEquals(Optional.empty(), node.get("key1")); // expired via lazy check
    }

    @Test
    void ttlReturnsRemainingTime() {
        node.put("key1", "value1", Duration.ofMinutes(5));

        Optional<Duration> remaining = node.ttl("key1");
        assertTrue(remaining.isPresent());
        assertTrue(remaining.get().toMinutes() >= 4);
    }

    @Test
    void ttlReturnsEmptyForNoTtl() {
        node.put("key1", "value1");

        Optional<Duration> remaining = node.ttl("key1");
        assertTrue(remaining.isEmpty());
    }

    @Test
    void clearRemovesAllEntries() {
        node.put("a", "1");
        node.put("b", "2");

        node.clear();

        assertEquals(0, node.size());
    }

    @Test
    void getAllEntriesExcludesExpired() throws InterruptedException {
        node.put("live", "value1");
        node.put("expiring", "value2", Duration.ofMillis(50));

        Thread.sleep(100);

        Map<String, CacheEntry<String, String>> entries = node.getAllEntries();
        assertEquals(1, entries.size());
        assertTrue(entries.containsKey("live"));
    }

    @Test
    void capacityAndIsFull() {
        assertEquals(3, node.capacity());
        assertFalse(node.isFull());

        node.put("a", "1");
        node.put("b", "2");
        node.put("c", "3");

        assertTrue(node.isFull());
    }

    @Test
    void nodeIdAndStatus() {
        assertEquals("test-node", node.getNodeId());
        assertEquals(CacheNodeStatus.ACTIVE, node.getStatus());
    }
}
