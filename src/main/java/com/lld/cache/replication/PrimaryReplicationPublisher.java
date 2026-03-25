package com.lld.cache.replication;

import com.lld.cache.config.NodeAddress;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs on PRIMARY. After each PUT or DELETE, asynchronously sends the
 * operation to every registered replica via POST /internal/replicate.
 */
public class PrimaryReplicationPublisher {

    private final List<NodeAddress> replicas;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public PrimaryReplicationPublisher(List<NodeAddress> replicas) {
        this.replicas = replicas;
        AtomicInteger threadCount = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, replicas.size()),
                r -> {
                    Thread t = new Thread(r, "repl-publisher-" + threadCount.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        this.httpClient = HttpClient.newBuilder()
                .executor(executor)
                .build();
    }

    public void replicatePut(String key, String value, long ttlSeconds) {
        String body = String.format(
                "{\"operation\":\"PUT\",\"key\":%s,\"value\":%s,\"ttl\":%d}",
                jsonStr(key), jsonStr(value), ttlSeconds);
        sendToAll(body);
    }

    public void replicateDelete(String key) {
        String body = String.format(
                "{\"operation\":\"DELETE\",\"key\":%s}",
                jsonStr(key));
        sendToAll(body);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendToAll(String body) {
        for (NodeAddress replica : replicas) {
            String url = "http://" + replica.getHost() + ":" + replica.getPort() + "/internal/replicate";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        System.err.printf("[Replication] Failed to reach replica %s — %s%n",
                                replica, e.getMessage());
                        return null;
                    });
        }
    }

    private String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
