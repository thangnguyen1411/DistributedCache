package com.lld.cache.config;

import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.exception.InvalidConfigException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CacheConfigTest {

    @Test
    void builderWithDefaults() {
        CacheConfig config = new CacheConfig.Builder().build();

        assertEquals(1000, config.getMaxEntriesPerNode());
        assertEquals(EvictionPolicyType.LRU, config.getEvictionPolicyType());
        assertEquals(Duration.ofMinutes(30), config.getDefaultTtl());
        assertEquals(2, config.getReplicationFactor());
        assertEquals(ReplicationMode.ASYNC, config.getReplicationMode());
        assertEquals(150, config.getVirtualNodeCount());
    }

    @Test
    void builderWithCustomValues() {
        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(500)
                .evictionPolicy(EvictionPolicyType.LFU)
                .defaultTtl(Duration.ofHours(1))
                .replicationFactor(3)
                .replicationMode(ReplicationMode.SYNC)
                .virtualNodeCount(200)
                .build();

        assertEquals(500, config.getMaxEntriesPerNode());
        assertEquals(EvictionPolicyType.LFU, config.getEvictionPolicyType());
        assertEquals(Duration.ofHours(1), config.getDefaultTtl());
        assertEquals(3, config.getReplicationFactor());
        assertEquals(ReplicationMode.SYNC, config.getReplicationMode());
        assertEquals(200, config.getVirtualNodeCount());
    }

    @Test
    void invalidMaxEntriesThrows() {
        assertThrows(InvalidConfigException.class, () ->
                new CacheConfig.Builder().maxEntriesPerNode(0).build());

        assertThrows(InvalidConfigException.class, () ->
                new CacheConfig.Builder().maxEntriesPerNode(-1).build());
    }

    @Test
    void invalidReplicationFactorThrows() {
        assertThrows(InvalidConfigException.class, () ->
                new CacheConfig.Builder().replicationFactor(0).build());
    }

    @Test
    void invalidVirtualNodeCountThrows() {
        assertThrows(InvalidConfigException.class, () ->
                new CacheConfig.Builder().virtualNodeCount(0).build());
    }
}
