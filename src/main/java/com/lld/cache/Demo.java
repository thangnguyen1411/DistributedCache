package com.lld.cache;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.event.LoggingCacheEventListener;

import java.time.Duration;

public class Demo {

    public static void main(String[] args) throws InterruptedException {
        // 1. Build configuration
        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(100)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(2)
                .replicationMode(ReplicationMode.SYNC)
                .defaultTtl(Duration.ofMinutes(10))
                .build();

        // 2. Create cache
        DistributedCache<String, String> cache = new DistributedCache<>(config);

        // 3. Subscribe to events
        cache.subscribe(new LoggingCacheEventListener());

        // 4. Add nodes
        System.out.println("=== Adding 3 nodes ===");
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");
        System.out.println("Nodes: " + cache.getNodeIds());

        // 5. PUT / GET / DELETE
        System.out.println("\n=== Basic Operations ===");
        cache.put("user:1", "Alice");
        cache.put("user:2", "Bob");
        cache.put("user:3", "Charlie");

        System.out.println("GET user:1 -> " + cache.get("user:1"));
        System.out.println("GET user:2 -> " + cache.get("user:2"));
        System.out.println("EXISTS user:3 -> " + cache.exists("user:3"));

        cache.delete("user:2");
        System.out.println("After DELETE user:2 -> " + cache.get("user:2"));

        // 6. TTL expiration
        System.out.println("\n=== TTL Expiration ===");
        cache.put("session:abc", "token-xyz", Duration.ofSeconds(1));
        System.out.println("GET session:abc -> " + cache.get("session:abc"));
        Thread.sleep(1500);
        System.out.println("After 1.5s -> " + cache.get("session:abc"));

        // 7. Dynamic node removal
        System.out.println("\n=== Node Removal ===");
        cache.put("product:1", "Laptop");
        cache.put("product:2", "Phone");
        System.out.println("Before removal: product:1 -> " + cache.get("product:1"));

        cache.removeNode("node-2");
        System.out.println("Nodes after removal: " + cache.getNodeIds());
        System.out.println("After removal: product:1 -> " + cache.get("product:1"));

        // 8. Cleanup
        cache.shutdown();
        System.out.println("\n=== Done ===");
    }
}
