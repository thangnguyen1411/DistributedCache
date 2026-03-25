package com.lld.cache.config;

import com.lld.cache.eviction.EvictionPolicyType;
import com.lld.cache.exception.InvalidConfigException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads and parses configuration.yml into ServerConfig.
 * Resolution order:
 *   1. Explicit file path (--config=path)
 *   2. ./application.yml in current working directory
 *   3. application.yml on the classpath
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
        String id    = string(server, "id", "server");
        int port     = integer(server, "port", 8080);
        ServerRole role = ServerRole.valueOf(string(server, "role", "PRIMARY").toUpperCase());

        // --- cache ---
        Map<String, Object> cache = section(root, "cache");
        int maxEntries         = integer(cache, "maxEntriesPerNode", 10_000);
        String evictionPolicy  = string(cache, "evictionPolicy", "LRU");
        long defaultTtlSec     = longVal(cache, "defaultTtlSeconds", 3600L);
        int virtualNodes       = integer(cache, "virtualNodeCount", 150);
        long expiryIntervalSec = longVal(cache, "expirationCleanupIntervalSeconds", 1L);

        // --- node count (names are generated internally, never exposed to users) ---
        int nodeCount = integer(cache, "nodeCount", 3);

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

        ServerConfig base = new ServerConfig(id, port, role, maxEntries, evictionPolicy, defaultTtlSec,
                virtualNodes, expiryIntervalSec, nodeCount, replicas, primary);

        return applyEnvOverrides(base);
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

    // ─── environment variable overrides ─────────────────────────────────────
    //
    // Env vars take precedence over the YAML file.
    // This is the standard pattern for Docker / AWS ECS / Kubernetes.
    //
    // Supported variables:
    //   CACHE_SERVER_ID       overrides server.id
    //   CACHE_SERVER_PORT     overrides server.port
    //   CACHE_SERVER_ROLE     overrides server.role  (PRIMARY or REPLICA)
    //   CACHE_NODE_COUNT      overrides cache.nodeCount
    //   CACHE_REPLICAS        overrides replication.replicas  format: host:port,host:port
    //   CACHE_PRIMARY         overrides replication.primary   format: host:port

    private static ServerConfig applyEnvOverrides(ServerConfig c) {
        String id     = envOr("CACHE_SERVER_ID",   c.getId());
        int port      = intEnvOr("CACHE_SERVER_PORT", c.getPort());
        ServerRole role = c.getRole();
        String roleEnv = System.getenv("CACHE_SERVER_ROLE");
        if (roleEnv != null) role = ServerRole.valueOf(roleEnv.toUpperCase());

        int nodeCount = intEnvOr("CACHE_NODE_COUNT", c.getNodeCount());

        List<NodeAddress> replicas = c.getReplicas();
        String replicasEnv = System.getenv("CACHE_REPLICAS");
        if (replicasEnv != null && !replicasEnv.isBlank()) {
            replicas = Arrays.stream(replicasEnv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(ConfigLoader::parseAddress)
                    .collect(Collectors.toList());
        }

        NodeAddress primary = c.getPrimary();
        String primaryEnv = System.getenv("CACHE_PRIMARY");
        if (primaryEnv != null && !primaryEnv.isBlank()) {
            primary = parseAddress(primaryEnv.trim());
        }

        return new ServerConfig(id, port, role, c.getMaxEntriesPerNode(), c.getEvictionPolicy(),
                c.getDefaultTtlSeconds(), c.getVirtualNodeCount(),
                c.getExpirationCleanupIntervalSeconds(), nodeCount, replicas, primary);
    }

    private static NodeAddress parseAddress(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) {
            throw new InvalidConfigException("Invalid address format (expected host:port): " + hostPort);
        }
        return new NodeAddress(parts[0], Integer.parseInt(parts[1]));
    }

    private static String envOr(String envKey, String fallback) {
        String v = System.getenv(envKey);
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    private static int intEnvOr(String envKey, int fallback) {
        String v = System.getenv(envKey);
        return (v != null && !v.isBlank()) ? Integer.parseInt(v) : fallback;
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
