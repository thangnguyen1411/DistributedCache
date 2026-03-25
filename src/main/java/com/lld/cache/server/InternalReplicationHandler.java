package com.lld.cache.server;

import com.lld.cache.DistributedCache;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles POST /internal/replicate on REPLICA nodes.
 * Receives replicated operations from the PRIMARY and applies them locally.
 *
 * Expected body:
 *   PUT    {"operation":"PUT","key":"k","value":"v","ttl":3600}
 *   DELETE {"operation":"DELETE","key":"k"}
 */
public class InternalReplicationHandler {

    private final DistributedCache<String, String> cache;
    private final long defaultTtlSeconds;

    public InternalReplicationHandler(DistributedCache<String, String> cache, long defaultTtlSeconds) {
        this.cache = cache;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public FullHttpResponse handle(String body, ResponseBuilder responseBuilder) {
        String operation = extractField(body, "operation");
        String key       = extractField(body, "key");

        if (key == null || operation == null) {
            return responseBuilder.build(HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"Missing 'operation' or 'key'\"}");
        }

        switch (operation.toUpperCase()) {
            case "PUT" -> {
                String value  = extractField(body, "value");
                String ttlStr = extractField(body, "ttl");
                if (value == null) {
                    return responseBuilder.build(HttpResponseStatus.BAD_REQUEST,
                            "{\"error\":\"Missing 'value' for PUT\"}");
                }
                long ttl = ttlStr != null ? Long.parseLong(ttlStr) : defaultTtlSeconds;
                cache.put(key, value, Duration.ofSeconds(ttl));
                System.out.printf("[Replica] Replicated PUT  key=%s ttl=%ds%n", key, ttl);
                return responseBuilder.build(HttpResponseStatus.OK,
                        "{\"status\":\"replicated\",\"operation\":\"PUT\"}");
            }
            case "DELETE" -> {
                cache.delete(key);
                System.out.printf("[Replica] Replicated DELETE key=%s%n", key);
                return responseBuilder.build(HttpResponseStatus.OK,
                        "{\"status\":\"replicated\",\"operation\":\"DELETE\"}");
            }
            default -> {
                return responseBuilder.build(HttpResponseStatus.BAD_REQUEST,
                        "{\"error\":\"Unknown operation: " + operation + "\"}");
            }
        }
    }

    private String extractField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Functional interface so the handler can build Netty responses without a direct Netty dependency. */
    @FunctionalInterface
    public interface ResponseBuilder {
        FullHttpResponse build(HttpResponseStatus status, String json);
    }
}
