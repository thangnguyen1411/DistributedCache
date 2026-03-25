package com.lld.cache.config;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Parsed representation of configuration.yml.
 * Constructed only by ConfigLoader.
 */
public class ServerConfig {

    private final String id;
    private final int port;
    private final ServerRole role;
    private final int maxEntriesPerNode;
    private final String evictionPolicy;
    private final long defaultTtlSeconds;
    private final int virtualNodeCount;
    private final long expirationCleanupIntervalSeconds;
    private final int nodeCount;
    private final List<NodeAddress> replicas;   // non-null, empty when role=REPLICA
    private final NodeAddress primary;          // null when role=PRIMARY

    public ServerConfig(String id,
                        int port,
                        ServerRole role,
                        int maxEntriesPerNode,
                        String evictionPolicy,
                        long defaultTtlSeconds,
                        int virtualNodeCount,
                        long expirationCleanupIntervalSeconds,
                        int nodeCount,
                        List<NodeAddress> replicas,
                        NodeAddress primary) {
        this.id = id;
        this.port = port;
        this.role = role;
        this.maxEntriesPerNode = maxEntriesPerNode;
        this.evictionPolicy = evictionPolicy;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.virtualNodeCount = virtualNodeCount;
        this.expirationCleanupIntervalSeconds = expirationCleanupIntervalSeconds;
        this.nodeCount = nodeCount;
        this.replicas = replicas;
        this.primary = primary;
    }

    public String getId()                           { return id; }
    public int getPort()                            { return port; }
    public ServerRole getRole()                     { return role; }
    public int getMaxEntriesPerNode()               { return maxEntriesPerNode; }
    public String getEvictionPolicy()               { return evictionPolicy; }
    public long getDefaultTtlSeconds()              { return defaultTtlSeconds; }
    public int getVirtualNodeCount()                { return virtualNodeCount; }
    public long getExpirationCleanupIntervalSeconds(){ return expirationCleanupIntervalSeconds; }
    public int getNodeCount()                       { return nodeCount; }
    public List<NodeAddress> getReplicas()          { return replicas; }
    public NodeAddress getPrimary()                 { return primary; }

    /**
     * Generates stable, deterministic partition IDs scoped to this server's ID.
     * e.g. primary-1-partition-0, primary-1-partition-1, primary-1-partition-2
     * Stable across restarts because the ID comes from config, not random generation.
     */
    public List<String> generateNodeIds() {
        return IntStream.range(0, nodeCount)
                .mapToObj(i -> id + "-partition-" + i)
                .toList();
    }
}
