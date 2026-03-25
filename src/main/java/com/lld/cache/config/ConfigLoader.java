package com.lld.cache.config;

import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.exception.InvalidConfigException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads and parses configuration.yml into ServerConfig.
 * Resolution order:
 *   1. Explicit file path (--config=path)
 *   2. ./configuration.yml in current working directory
 *   3. configuration.yml on the classpath
 */
public class ConfigLoader {

    public static ServerConfig load(String path) {
        // 1. Try as explicit filesystem path
        File file = new File(path);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                return parse(is);
            } catch (Exception e) {
                throw new InvalidConfigException("Failed to load config from " + path + ": " + e.getMessage());
            }
        }

        // 2. Try classpath
        try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new InvalidConfigException("Config file not found: " + path);
            }
            return parse(is);
        } catch (InvalidConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidConfigException("Failed to load config from classpath/" + path + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static ServerConfig parse(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(is);

        // --- server ---
        Map<String, Object> server = section(root, "server");
        int port     = integer(server, "port", 8080);
        ServerRole role = ServerRole.valueOf(string(server, "role", "PRIMARY").toUpperCase());

        // --- cache ---
        Map<String, Object> cache = section(root, "cache");
        int maxEntries         = integer(cache, "maxEntriesPerNode", 10_000);
        String evictionPolicy  = string(cache, "evictionPolicy", "LRU");
        long defaultTtlSec     = longVal(cache, "defaultTtlSeconds", 3600L);
        int virtualNodes       = integer(cache, "virtualNodeCount", 150);
        long expiryIntervalSec = longVal(cache, "expirationCleanupIntervalSeconds", 1L);

        // --- nodes ---
        List<String> nodes = (List<String>) root.getOrDefault("nodes",
                List.of("node-1", "node-2", "node-3"));

        // --- replication ---
        Map<String, Object> replication = section(root, "replication");

        List<NodeAddress> replicas = Collections.emptyList();
        List<Map<String, Object>> replicasRaw =
                (List<Map<String, Object>>) replication.getOrDefault("replicas", List.of());
        if (!replicasRaw.isEmpty()) {
            replicas = replicasRaw.stream()
                    .map(m -> new NodeAddress(
                            (String) m.get("host"),
                            (int) m.get("port")))
                    .collect(Collectors.toList());
        }

        NodeAddress primary = null;
        Map<String, Object> primaryRaw = (Map<String, Object>) replication.get("primary");
        if (primaryRaw != null) {
            primary = new NodeAddress((String) primaryRaw.get("host"), (int) primaryRaw.get("port"));
        }

        // Validate
        EvictionPolicyType.valueOf(evictionPolicy.toUpperCase()); // throws if unknown

        return new ServerConfig(port, role, maxEntries, evictionPolicy, defaultTtlSec,
                virtualNodes, expiryIntervalSec, nodes, replicas, primary);
    }

    /** Converts ServerConfig into the existing CacheConfig used by CacheManager. */
    public static CacheConfig toCacheConfig(ServerConfig sc) {
        return new CacheConfig.Builder()
                .maxEntriesPerNode(sc.getMaxEntriesPerNode())
                .evictionPolicy(EvictionPolicyType.valueOf(sc.getEvictionPolicy().toUpperCase()))
                .replicationFactor(1)           // intra-node replication disabled; inter-process via primary-replica
                .replicationMode(ReplicationMode.ASYNC)
                .defaultTtl(Duration.ofSeconds(sc.getDefaultTtlSeconds()))
                .virtualNodeCount(sc.getVirtualNodeCount())
                .expirationCleanupInterval(Duration.ofSeconds(sc.getExpirationCleanupIntervalSeconds()))
                .build();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object v = root.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return Collections.emptyMap();
    }

    private static String string(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : def;
    }

    private static int integer(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    private static long longVal(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }
}
