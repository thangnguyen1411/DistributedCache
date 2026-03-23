package com.lld.cache.config;

import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.exception.InvalidConfigException;

import java.time.Duration;

public class CacheConfig {

    private final int maxEntriesPerNode;
    private final EvictionPolicyType evictionPolicyType;
    private final Duration defaultTtl;
    private final int replicationFactor;
    private final ReplicationMode replicationMode;
    private final int virtualNodeCount;
    private final Duration expirationCleanupInterval;

    private CacheConfig(Builder builder) {
        this.maxEntriesPerNode = builder.maxEntriesPerNode;
        this.evictionPolicyType = builder.evictionPolicyType;
        this.defaultTtl = builder.defaultTtl;
        this.replicationFactor = builder.replicationFactor;
        this.replicationMode = builder.replicationMode;
        this.virtualNodeCount = builder.virtualNodeCount;
        this.expirationCleanupInterval = builder.expirationCleanupInterval;
    }

    public int getMaxEntriesPerNode() {
        return maxEntriesPerNode;
    }

    public EvictionPolicyType getEvictionPolicyType() {
        return evictionPolicyType;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public ReplicationMode getReplicationMode() {
        return replicationMode;
    }

    public int getVirtualNodeCount() {
        return virtualNodeCount;
    }

    public Duration getExpirationCleanupInterval() {
        return expirationCleanupInterval;
    }

    public static class Builder {

        private int maxEntriesPerNode = 1000;
        private EvictionPolicyType evictionPolicyType = EvictionPolicyType.LRU;
        private Duration defaultTtl = Duration.ofMinutes(30);
        private int replicationFactor = 2;
        private ReplicationMode replicationMode = ReplicationMode.ASYNC;
        private int virtualNodeCount = 150;
        private Duration expirationCleanupInterval = Duration.ofSeconds(1);

        public Builder maxEntriesPerNode(int maxEntriesPerNode) {
            this.maxEntriesPerNode = maxEntriesPerNode;
            return this;
        }

        public Builder evictionPolicy(EvictionPolicyType evictionPolicyType) {
            this.evictionPolicyType = evictionPolicyType;
            return this;
        }

        public Builder defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
            return this;
        }

        public Builder replicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }

        public Builder replicationMode(ReplicationMode replicationMode) {
            this.replicationMode = replicationMode;
            return this;
        }

        public Builder virtualNodeCount(int virtualNodeCount) {
            this.virtualNodeCount = virtualNodeCount;
            return this;
        }

        public Builder expirationCleanupInterval(Duration expirationCleanupInterval) {
            this.expirationCleanupInterval = expirationCleanupInterval;
            return this;
        }

        public CacheConfig build() {
            validate();
            return new CacheConfig(this);
        }

        private void validate() {
            if (maxEntriesPerNode <= 0) {
                throw new InvalidConfigException("maxEntriesPerNode must be positive");
            }
            if (replicationFactor < 1) {
                throw new InvalidConfigException("replicationFactor must be at least 1");
            }
            if (virtualNodeCount <= 0) {
                throw new InvalidConfigException("virtualNodeCount must be positive");
            }
            if (evictionPolicyType == null) {
                throw new InvalidConfigException("evictionPolicyType must not be null");
            }
            if (replicationMode == null) {
                throw new InvalidConfigException("replicationMode must not be null");
            }
            if (expirationCleanupInterval == null || expirationCleanupInterval.isNegative()) {
                throw new InvalidConfigException("expirationCleanupInterval must be non-negative");
            }
        }
    }
}
