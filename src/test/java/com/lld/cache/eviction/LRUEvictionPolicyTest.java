package com.lld.cache.eviction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LRUEvictionPolicyTest {

    private LRUEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new LRUEvictionPolicy<>();
    }

    @Test
    void evictReturnsEmptyWhenNoKeys() {
        assertEquals(Optional.empty(), policy.evict());
    }

    @Test
    void evictsLeastRecentlyUsedKey() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        assertEquals(Optional.of("a"), policy.evict());
    }

    @Test
    void accessMovesKeyToEnd() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        policy.keyAccessed("a");

        assertEquals(Optional.of("b"), policy.evict());
    }

    @Test
    void keyRemovedPreventsEviction() {
        policy.keyAdded("a");
        policy.keyAdded("b");

        policy.keyRemoved("a");

        assertEquals(Optional.of("b"), policy.evict());
        assertEquals(0, policy.size());
    }

    @Test
    void sizeTracksCorrectly() {
        assertEquals(0, policy.size());

        policy.keyAdded("a");
        assertEquals(1, policy.size());

        policy.keyAdded("b");
        assertEquals(2, policy.size());

        policy.evict();
        assertEquals(1, policy.size());
    }

    @Test
    void multipleEvictionsInOrder() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        assertEquals(Optional.of("a"), policy.evict());
        assertEquals(Optional.of("b"), policy.evict());
        assertEquals(Optional.of("c"), policy.evict());
        assertEquals(Optional.empty(), policy.evict());
    }
}
