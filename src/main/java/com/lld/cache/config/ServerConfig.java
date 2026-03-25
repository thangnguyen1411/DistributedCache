package com.lld.cache.config;

import java.util.List;

/**
 * Parsed representation of configuration.yml.
 * Constructed only by ConfigLoader.
 */
public class ServerConfig {

    private final int port;
    private final ServerRole role;
    private final int maxEntriesPerNode;
    private final String evictionPolicy;
    private final long defaultTtlSeconds;
    private final int virtualNodeCount;
    private final long expirationCleanupIntervalSeconds;
    private final List<String> nodes;
    private final List<NodeAddress> replicas;   // non-null, empty when role=REPLICA
    private final NodeAddress primary;          // null when role=PRIMARY

    public ServerConfig(int port,
                        ServerRole role,
                        int maxEntriesPerNode,
                        String evictionPolicy,
                        long defaultTtlSeconds,
                        int virtualNodeCount,
                        long expirationCleanupIntervalSeconds,
                        List<String> nodes,
                        List<NodeAddress> replicas,
                        NodeAddress primary) {
        this.port = port;
        this.role = role;
        this.maxEntriesPerNode = maxEntriesPerNode;
        this.evictionPolicy = evictionPolicy;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.virtualNodeCount = virtualNodeCount;
        this.expirationCleanupIntervalSeconds = expirationCleanupIntervalSeconds;
        this.nodes = nodes;
        this.replicas = replicas;
        this.primary = primary;
    }

    public int getPort()                            { return port; }
    public ServerRole getRole()                     { return role; }
    public int getMaxEntriesPerNode()               { return maxEntriesPerNode; }
    public String getEvictionPolicy()               { return evictionPolicy; }
    public long getDefaultTtlSeconds()              { return defaultTtlSeconds; }
    public int getVirtualNodeCount()                { return virtualNodeCount; }
    public long getExpirationCleanupIntervalSeconds(){ return expirationCleanupIntervalSeconds; }
    public List<String> getNodes()                  { return nodes; }
    public List<NodeAddress> getReplicas()          { return replicas; }
    public NodeAddress getPrimary()                 { return primary; }
}
