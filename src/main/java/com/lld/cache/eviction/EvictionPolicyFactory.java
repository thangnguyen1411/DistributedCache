package com.lld.cache.eviction;

public class EvictionPolicyFactory {

    private EvictionPolicyFactory() {
    }

    public static <K> EvictionPolicy<K> create(EvictionPolicyType type) {
        return switch (type) {
            case LRU -> new LRUEvictionPolicy<>();
            case LFU -> new LFUEvictionPolicy<>();
            case FIFO -> new FIFOEvictionPolicy<>();
        };
    }
}
