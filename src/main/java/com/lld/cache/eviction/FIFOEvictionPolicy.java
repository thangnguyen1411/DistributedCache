package com.lld.cache.eviction;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class FIFOEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Queue<K> queue = new LinkedList<>();
    private final Set<K> keys = new HashSet<>();

    @Override
    public void keyAccessed(K key) {
        // FIFO does not reorder on access
    }

    @Override
    public void keyAdded(K key) {
        queue.add(key);
        keys.add(key);
    }

    @Override
    public void keyRemoved(K key) {
        keys.remove(key);
        // Lazy removal from queue — skip stale entries during evict()
    }

    @Override
    public Optional<K> evict() {
        while (!queue.isEmpty()) {
            K candidate = queue.poll();
            if (keys.remove(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    @Override
    public int size() {
        return keys.size();
    }
}
