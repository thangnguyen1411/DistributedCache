package com.lld.cache.eviction;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class LFUEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Map<K, Integer> keyFrequency = new HashMap<>();
    private final TreeMap<Integer, LinkedHashSet<K>> frequencyBuckets = new TreeMap<>();

    @Override
    public void keyAccessed(K key) {
        Integer freq = keyFrequency.get(key);
        if (freq == null) {
            return;
        }
        removeFromBucket(freq, key);
        int newFreq = freq + 1;
        keyFrequency.put(key, newFreq);
        frequencyBuckets.computeIfAbsent(newFreq, k -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public void keyAdded(K key) {
        keyFrequency.put(key, 1);
        frequencyBuckets.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public void keyRemoved(K key) {
        Integer freq = keyFrequency.remove(key);
        if (freq != null) {
            removeFromBucket(freq, key);
        }
    }

    @Override
    public Optional<K> evict() {
        if (frequencyBuckets.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<Integer, LinkedHashSet<K>> lowestEntry = frequencyBuckets.firstEntry();
        LinkedHashSet<K> keys = lowestEntry.getValue();
        K victim = keys.iterator().next();
        keys.remove(victim);
        if (keys.isEmpty()) {
            frequencyBuckets.remove(lowestEntry.getKey());
        }
        keyFrequency.remove(victim);
        return Optional.of(victim);
    }

    @Override
    public int size() {
        return keyFrequency.size();
    }

    private void removeFromBucket(int freq, K key) {
        LinkedHashSet<K> bucket = frequencyBuckets.get(freq);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                frequencyBuckets.remove(freq);
            }
        }
    }
}
