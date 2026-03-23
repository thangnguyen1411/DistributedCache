package com.lld.cache;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.event.CacheEventBus;
import com.lld.cache.event.CacheEventListener;
import com.lld.cache.exception.NodeNotFoundException;
import com.lld.cache.expiration.ExpirationManager;
import com.lld.cache.hash.ConsistentHashRing;
import com.lld.cache.hash.MD5HashFunction;
import com.lld.cache.model.CacheEntry;
import com.lld.cache.model.CacheEvent;
import com.lld.cache.model.CacheEventType;
import com.lld.cache.node.CacheNode;
import com.lld.cache.node.CacheNodeFactory;
import com.lld.cache.replication.AsyncReplicationStrategy;
import com.lld.cache.replication.ReplicationManager;
import com.lld.cache.replication.ReplicationStrategy;
import com.lld.cache.replication.SyncReplicationStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheManager<K, V> {

    private static volatile CacheManager<?, ?> instance;

    private final CacheConfig config;
    private final ConsistentHashRing hashRing;
    private final ReplicationManager<K, V> replicationManager;
    private final ExpirationManager<K, V> expirationManager;
    private final CacheEventBus eventBus;
    private final Map<String, CacheNode<K, V>> nodes = new ConcurrentHashMap<>();
    private final ReadWriteLock nodesLock = new ReentrantReadWriteLock();

    private CacheManager(CacheConfig config) {
        this.config = config;
        this.eventBus = new CacheEventBus();
        this.hashRing = new ConsistentHashRing(config.getVirtualNodeCount(), new MD5HashFunction());

        ReplicationStrategy<K, V> strategy = createReplicationStrategy(config);
        this.replicationManager = new ReplicationManager<>(hashRing, strategy,
                config.getReplicationFactor(), nodes);

        this.expirationManager = new ExpirationManager<>(config.getExpirationCleanupInterval());
        this.expirationManager.start();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> CacheManager<K, V> getInstance(CacheConfig config) {
        if (instance == null) {
            synchronized (CacheManager.class) {
                if (instance == null) {
                    instance = new CacheManager<K, V>(config);
                }
            }
        }
        return (CacheManager<K, V>) instance;
    }

    public static void resetInstance() {
        synchronized (CacheManager.class) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    public Optional<V> get(K key) {
        nodesLock.readLock().lock();
        try {
            CacheNode<K, V> node = resolveNode(key);
            if (node == null) {
                return Optional.empty();
            }
            Optional<V> result = node.get(key);
            if (result.isEmpty() && config.getReplicationFactor() > 1) {
                // Try replicas
                List<String> replicaIds = hashRing.getReplicaNodesForKey(
                        String.valueOf(key), config.getReplicationFactor() - 1);
                for (String replicaId : replicaIds) {
                    CacheNode<K, V> replica = nodes.get(replicaId);
                    if (replica != null) {
                        result = replica.get(key);
                        if (result.isPresent()) {
                            return result;
                        }
                    }
                }
            }
            return result;
        } finally {
            nodesLock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        put(key, value, config.getDefaultTtl());
    }

    public void put(K key, V value, Duration ttl) {
        nodesLock.readLock().lock();
        try {
            CacheNode<K, V> node = resolveNode(key);
            if (node == null) {
                return;
            }
            node.put(key, value, ttl);
            // Replicate
            CacheEntry<K, V> entry = new CacheEntry<>(key, value, ttl);
            replicationManager.replicate(key, entry);
        } finally {
            nodesLock.readLock().unlock();
        }
    }

    public boolean delete(K key) {
        nodesLock.readLock().lock();
        try {
            CacheNode<K, V> node = resolveNode(key);
            if (node == null) {
                return false;
            }
            boolean deleted = node.delete(key);
            if (deleted) {
                replicationManager.replicateDelete(key);
            }
            return deleted;
        } finally {
            nodesLock.readLock().unlock();
        }
    }

    public boolean exists(K key) {
        nodesLock.readLock().lock();
        try {
            CacheNode<K, V> node = resolveNode(key);
            return node != null && node.exists(key);
        } finally {
            nodesLock.readLock().unlock();
        }
    }

    public CacheNode<K, V> addNode(String nodeId) {
        nodesLock.writeLock().lock();
        try {
            CacheNode<K, V> node = CacheNodeFactory.createNode(nodeId, config, eventBus);
            nodes.put(nodeId, node);
            hashRing.addNode(nodeId);
            expirationManager.addNode(node);
            eventBus.publish(new CacheEvent(CacheEventType.NODE_ADDED, null, nodeId));
            return node;
        } finally {
            nodesLock.writeLock().unlock();
        }
    }

    public void removeNode(String nodeId) {
        nodesLock.writeLock().lock();
        try {
            CacheNode<K, V> node = nodes.get(nodeId);
            if (node == null) {
                throw new NodeNotFoundException(nodeId);
            }

            // Redistribute entries before removing
            Map<K, CacheEntry<K, V>> entries = node.getAllEntries();
            hashRing.removeNode(nodeId);
            nodes.remove(nodeId);
            expirationManager.removeNode(node);

            // Redistribute entries to new owners
            for (Map.Entry<K, CacheEntry<K, V>> entry : entries.entrySet()) {
                String newNodeId = hashRing.getNodeForKey(String.valueOf(entry.getKey()));
                if (newNodeId != null) {
                    CacheNode<K, V> newNode = nodes.get(newNodeId);
                    if (newNode != null) {
                        newNode.putEntry(entry.getKey(), entry.getValue());
                        replicationManager.replicate(entry.getKey(), entry.getValue());
                    }
                }
            }

            eventBus.publish(new CacheEvent(CacheEventType.NODE_REMOVED, null, nodeId));
        } finally {
            nodesLock.writeLock().unlock();
        }
    }

    public List<String> getNodeIds() {
        nodesLock.readLock().lock();
        try {
            return new ArrayList<>(nodes.keySet());
        } finally {
            nodesLock.readLock().unlock();
        }
    }

    public void addEventListener(CacheEventListener listener) {
        eventBus.register(listener);
    }

    public void removeEventListener(CacheEventListener listener) {
        eventBus.unregister(listener);
    }

    public void shutdown() {
        expirationManager.stop();
        eventBus.shutdown();
        ReplicationStrategy<K, V> strategy = replicationManager.getStrategy();
        if (strategy instanceof AsyncReplicationStrategy<K, V> async) {
            async.shutdown();
        }
        nodes.clear();
    }

    public CacheConfig getConfig() {
        return config;
    }

    private CacheNode<K, V> resolveNode(K key) {
        String nodeId = hashRing.getNodeForKey(String.valueOf(key));
        if (nodeId == null) {
            return null;
        }
        return nodes.get(nodeId);
    }

    private ReplicationStrategy<K, V> createReplicationStrategy(CacheConfig config) {
        if (config.getReplicationMode() == ReplicationMode.SYNC) {
            return new SyncReplicationStrategy<>();
        }
        return new AsyncReplicationStrategy<>();
    }
}
