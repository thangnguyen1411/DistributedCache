package com.lld.cache.eviction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FIFOEvictionPolicyTest {

    private FIFOEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new FIFOEvictionPolicy<>();
    }

    @Test
    void evictReturnsEmptyWhenNoKeys() {
        assertEquals(Optional.empty(), policy.evict());
    }

    @Test
    void evictsInInsertionOrder() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        assertEquals(Optional.of("a"), policy.evict());
        assertEquals(Optional.of("b"), policy.evict());
        assertEquals(Optional.of("c"), policy.evict());
    }

    @Test
    void accessDoesNotReorder() {
        policy.keyAdded("a");
        policy.keyAdded("b");

        policy.keyAccessed("a"); // no-op for FIFO

        assertEquals(Optional.of("a"), policy.evict());
    }

    @Test
    void keyRemovedSkipsStaleEntries() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        policy.keyRemoved("b"); // lazy removal

        assertEquals(Optional.of("a"), policy.evict());
        assertEquals(Optional.of("c"), policy.evict()); // "b" skipped
    }

    @Test
    void sizeTracksCorrectly() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        assertEquals(2, policy.size());

        policy.keyRemoved("a");
        assertEquals(1, policy.size());
    }
}
