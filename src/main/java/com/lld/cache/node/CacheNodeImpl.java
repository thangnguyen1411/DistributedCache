package com.lld.cache.node;

import com.lld.cache.eviction.EvictionPolicy;
import com.lld.cache.event.CacheEventBus;
import com.lld.cache.expiration.LazyExpirationChecker;
import com.lld.cache.model.CacheEntry;
import com.lld.cache.model.CacheEvent;
import com.lld.cache.model.CacheEventType;
import com.lld.cache.storage.CacheStore;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheNodeImpl<K, V> implements CacheNode<K, V> {

    private final String nodeId;
    private final int capacity;
    private final CacheStore<K, V> store;
    private final EvictionPolicy<K> evictionPolicy;
    private final LazyExpirationChecker<K, V> expirationChecker;
    private final CacheEventBus eventBus;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile CacheNodeStatus status = CacheNodeStatus.ACTIVE;

    public CacheNodeImpl(String nodeId, int capacity, CacheStore<K, V> store,
                         EvictionPolicy<K> evictionPolicy,
                         LazyExpirationChecker<K, V> expirationChecker,
                         CacheEventBus eventBus) {
        this.nodeId = nodeId;
        this.capacity = capacity;
        this.store = store;
        this.evictionPolicy = evictionPolicy;
        this.expirationChecker = expirationChecker;
        this.eventBus = eventBus;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public CacheNodeStatus getStatus() {
        return status;
    }

    @Override
    public Optional<V> get(K key) {
        lock.readLock().lock();
        try {
            Optional<CacheEntry<K, V>> optEntry = store.get(key);
            if (optEntry.isEmpty()) {
                return Optional.empty();
            }
            CacheEntry<K, V> entry = optEntry.get();
            if (expirationChecker.isExpired(entry)) {
                // Need write lock to remove — upgrade by releasing read first
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    // Re-check after acquiring write lock
                    optEntry = store.get(key);
                    if (optEntry.isPresent() && expirationChecker.isExpired(optEntry.get())) {
                        store.remove(key);
                        evictionPolicy.keyRemoved(key);
                        publishEvent(CacheEventType.EXPIRATION, key);
                    }
                    return Optional.empty();
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock(); // re-acquire read lock for finally block
                }
            }
            entry.recordAccess();
            evictionPolicy.keyAccessed(key);
            return Optional.of(entry.getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, null);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        lock.writeLock().lock();
        try {
            boolean isNew = !store.containsKey(key);
            if (isNew && store.size() >= capacity) {
                evictOne();
            }
            CacheEntry<K, V> entry = new CacheEntry<>(key, value, ttl);
            store.put(key, entry);
            if (isNew) {
                evictionPolicy.keyAdded(key);
            } else {
                evictionPolicy.keyAccessed(key);
            }
            publishEvent(CacheEventType.PUT, key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(K key) {
        lock.writeLock().lock();
        try {
            CacheEntry<K, V> removed = store.remove(key);
            if (removed != null) {
                evictionPolicy.keyRemoved(key);
                publishEvent(CacheEventType.DELETE, key);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists(K key) {
        lock.readLock().lock();
        try {
            Optional<CacheEntry<K, V>> optEntry = store.get(key);
            if (optEntry.isEmpty()) {
                return false;
            }
            return !expirationChecker.isExpired(optEntry.get());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Duration> ttl(K key) {
        lock.readLock().lock();
        try {
            return store.get(key)
                    .filter(e -> !expirationChecker.isExpired(e))
                    .map(CacheEntry::remainingTtl);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean isFull() {
        return size() >= capacity;
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            for (K key : store.keys()) {
                evictionPolicy.keyRemoved(key);
            }
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Map<K, CacheEntry<K, V>> getAllEntries() {
        lock.readLock().lock();
        try {
            Map<K, CacheEntry<K, V>> result = new HashMap<>();
            for (CacheEntry<K, V> entry : store.entries()) {
                if (!expirationChecker.isExpired(entry)) {
                    result.put(entry.getKey(), entry);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void putEntry(K key, CacheEntry<K, V> entry) {
        lock.writeLock().lock();
        try {
            boolean isNew = !store.containsKey(key);
            if (isNew && store.size() >= capacity) {
                evictOne();
            }
            store.put(key, entry);
            if (isNew) {
                evictionPolicy.keyAdded(key);
            } else {
                evictionPolicy.keyAccessed(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictOne() {
        Optional<K> victim = evictionPolicy.evict();
        if (victim.isPresent()) {
            store.remove(victim.get());
            publishEvent(CacheEventType.EVICTION, victim.get());
        }
    }

    private void publishEvent(CacheEventType type, K key) {
        if (eventBus != null) {
            eventBus.publish(new CacheEvent(type, String.valueOf(key), nodeId));
        }
    }
}
