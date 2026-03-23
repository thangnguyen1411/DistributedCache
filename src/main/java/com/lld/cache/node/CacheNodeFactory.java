package com.lld.cache.node;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.eviction.EvictionPolicy;
import com.lld.cache.eviction.EvictionPolicyFactory;
import com.lld.cache.event.CacheEventBus;
import com.lld.cache.expiration.LazyExpirationChecker;
import com.lld.cache.storage.InMemoryCacheStore;

public class CacheNodeFactory {

    private CacheNodeFactory() {
    }

    public static <K, V> CacheNode<K, V> createNode(String nodeId, CacheConfig config, CacheEventBus eventBus) {
        InMemoryCacheStore<K, V> store = new InMemoryCacheStore<>();
        EvictionPolicy<K> evictionPolicy = EvictionPolicyFactory.create(config.getEvictionPolicyType());
        LazyExpirationChecker<K, V> expirationChecker = new LazyExpirationChecker<>();
        return new CacheNodeImpl<>(nodeId, config.getMaxEntriesPerNode(),
                store, evictionPolicy, expirationChecker, eventBus);
    }
}
