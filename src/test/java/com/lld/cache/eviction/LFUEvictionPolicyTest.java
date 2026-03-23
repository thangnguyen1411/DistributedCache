package com.lld.cache.eviction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LFUEvictionPolicyTest {

    private LFUEvictionPolicy<String> policy;

    @BeforeEach
    void setUp() {
        policy = new LFUEvictionPolicy<>();
    }

    @Test
    void evictReturnsEmptyWhenNoKeys() {
        assertEquals(Optional.empty(), policy.evict());
    }

    @Test
    void evictsLeastFrequentlyUsedKey() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        policy.keyAccessed("b");
        policy.keyAccessed("c");
        policy.keyAccessed("c");

        // "a" has freq 1, "b" has freq 2, "c" has freq 3
        assertEquals(Optional.of("a"), policy.evict());
    }

    @Test
    void tieBreaksByInsertionOrder() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        policy.keyAdded("c");

        // All have frequency 1 — "a" was inserted first
        assertEquals(Optional.of("a"), policy.evict());
    }

    @Test
    void keyRemovedPreventsEviction() {
        policy.keyAdded("a");
        policy.keyAdded("b");

        policy.keyRemoved("a");

        assertEquals(Optional.of("b"), policy.evict());
    }

    @Test
    void sizeTracksCorrectly() {
        policy.keyAdded("a");
        policy.keyAdded("b");
        assertEquals(2, policy.size());

        policy.evict();
        assertEquals(1, policy.size());
    }

    @Test
    void frequencyBucketsCleanedUp() {
        policy.keyAdded("a");
        policy.keyAccessed("a"); // freq 2

        policy.keyAdded("b"); // freq 1

        assertEquals(Optional.of("b"), policy.evict());
        assertEquals(Optional.of("a"), policy.evict());
        assertEquals(Optional.empty(), policy.evict());
    }
}
