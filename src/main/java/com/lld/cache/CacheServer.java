package com.lld.cache;

import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.eviction.EvictionPolicyType;

import java.time.Duration;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interactive daemon that runs a DistributedCache and accepts commands from stdin.
 *
 * Usage:
 *   java -jar distributed-cache.jar
 *
 * Commands:
 *   put <key> <value>             – store key/value (uses default TTL)
 *   put <key> <value> <ttl_sec>  – store with explicit TTL in seconds
 *   get <key>                     – retrieve a value
 *   delete <key>                  – delete a key
 *   exists <key>                  – check if key exists
 *   addnode <nodeId>              – add a cache node
 *   removenode <nodeId>           – remove a cache node
 *   nodes                         – list active nodes
 *   help                          – show this help
 *   quit / exit                   – shutdown and exit
 */
public class CacheServer {

    private static final String PROMPT = "cache> ";

    public static void main(String[] args) {
        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(10_000)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(2)
                .replicationMode(ReplicationMode.ASYNC)
                .defaultTtl(Duration.ofHours(1))
                .build();

        DistributedCache<String, String> cache = new DistributedCache<>(config);

        // Start with 3 nodes by default
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down cache...");
            cache.shutdown();
        }));

        printBanner();
        System.out.println("Active nodes: " + cache.getNodeIds());
        System.out.println("Type 'help' for available commands.\n");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print(PROMPT);
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break; // EOF (piped input or Ctrl+D)
            }

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+", 4);
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "get" -> {
                        requireArgs(parts, 2, "get <key>");
                        Optional<String> val = cache.get(parts[1]);
                        System.out.println(val.isPresent() ? val.get() : "(nil)");
                    }
                    case "put" -> {
                        requireArgs(parts, 3, "put <key> <value> [ttl_seconds]");
                        if (parts.length == 4) {
                            long ttlSec = Long.parseLong(parts[3]);
                            cache.put(parts[1], parts[2], Duration.ofSeconds(ttlSec));
                        } else {
                            cache.put(parts[1], parts[2]);
                        }
                        System.out.println("OK");
                    }
                    case "delete", "del" -> {
                        requireArgs(parts, 2, "delete <key>");
                        boolean deleted = cache.delete(parts[1]);
                        System.out.println(deleted ? "(deleted)" : "(not found)");
                    }
                    case "exists" -> {
                        requireArgs(parts, 2, "exists <key>");
                        System.out.println(cache.exists(parts[1]) ? "true" : "false");
                    }
                    case "addnode" -> {
                        requireArgs(parts, 2, "addnode <nodeId>");
                        cache.addNode(parts[1]);
                        System.out.println("Node '" + parts[1] + "' added. Nodes: " + cache.getNodeIds());
                    }
                    case "removenode" -> {
                        requireArgs(parts, 2, "removenode <nodeId>");
                        cache.removeNode(parts[1]);
                        System.out.println("Node '" + parts[1] + "' removed. Nodes: " + cache.getNodeIds());
                    }
                    case "nodes" -> System.out.println(cache.getNodeIds());
                    case "help" -> printHelp();
                    case "quit", "exit" -> {
                        System.out.println("Bye!");
                        cache.shutdown();
                        System.exit(0);
                    }
                    default -> System.out.println("Unknown command: '" + cmd + "'. Type 'help' for usage.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void requireArgs(String[] parts, int minCount, String usage) {
        if (parts.length < minCount) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║      Distributed Cache Server        ║");
        System.out.println("╚══════════════════════════════════════╝");
    }

    private static void printHelp() {
        System.out.println("""
                Commands:
                  put <key> <value>              store key/value (default TTL)
                  put <key> <value> <ttl_sec>    store with TTL in seconds
                  get <key>                      retrieve a value (nil if missing/expired)
                  delete <key>                   delete a key
                  exists <key>                   check if key exists
                  addnode <nodeId>               add a cache node
                  removenode <nodeId>            remove a cache node
                  nodes                          list active nodes
                  help                           show this help
                  quit / exit                    shutdown and exit
                """);
    }
}
