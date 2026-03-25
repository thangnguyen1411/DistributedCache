package com.lld.cache.server;

import com.lld.cache.DistributedCache;
import com.lld.cache.config.ServerRole;
import com.lld.cache.exception.CacheFullException;
import com.lld.cache.exception.KeyNotFoundException;
import com.lld.cache.replication.PrimaryReplicationPublisher;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Netty channel handler — routes HTTP requests to cache operations.
 *
 * Role behaviour:
 *   PRIMARY — accepts reads + writes; replicates writes to replicas via PrimaryReplicationPublisher
 *   REPLICA — accepts reads only; rejects writes with 405; receives replication on /internal/replicate
 */
public class CacheHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final DistributedCache<String, String> cache;
    private final ServerRole role;
    private final long defaultTtlSeconds;
    private final PrimaryReplicationPublisher publisher;      // null on REPLICA
    private final InternalReplicationHandler internalHandler; // used on REPLICA

    public CacheHttpHandler(DistributedCache<String, String> cache,
                            ServerRole role,
                            long defaultTtlSeconds,
                            PrimaryReplicationPublisher publisher,
                            InternalReplicationHandler internalHandler) {
        this.cache = cache;
        this.role = role;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.publisher = publisher;
        this.internalHandler = internalHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String path       = req.uri();
        HttpMethod method = req.method();
        String body       = req.content().toString(StandardCharsets.UTF_8);

        FullHttpResponse response;
        try {
            response = route(method, path, body);
        } catch (KeyNotFoundException e) {
            response = json(HttpResponseStatus.NOT_FOUND, jsonError(e.getMessage()));
        } catch (CacheFullException e) {
            response = json(HttpResponseStatus.INSUFFICIENT_STORAGE, jsonError(e.getMessage()));
        } catch (Exception e) {
            response = json(HttpResponseStatus.INTERNAL_SERVER_ERROR, jsonError(e.getMessage()));
        }

        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    // ─── router ─────────────────────────────────────────────────────────────

    private FullHttpResponse route(HttpMethod method, String path, String body) {

        // Internal replication endpoint — always accessible regardless of role
        if (POST(method) && path.equals("/internal/replicate")) {
            return internalHandler.handle(body, this::json);
        }

        // Health
        if (GET(method) && path.equals("/health")) {
            return handleHealth();
        }

        // Nodes
        if (GET(method) && path.equals("/nodes")) {
            return handleListNodes();
        }

        Matcher m = match("/nodes/(.+)", path);
        if (m.matches()) {
            String nodeId = m.group(1);
            if (POST(method))   return handleAddNode(nodeId);
            if (DELETE(method)) return handleRemoveNode(nodeId);
        }

        // Cache — exists check
        m = match("/cache/(.+)/exists", path);
        if (m.matches()) {
            return handleExists(m.group(1));
        }

        // Cache — key operations
        m = match("/cache/(.+)", path);
        if (m.matches()) {
            String key = m.group(1);
            if (GET(method))    return handleGet(key);
            if (DELETE(method)) return handleDelete(key);
        }

        // Cache — put
        if (POST(method) && path.equals("/cache")) {
            return handlePut(body);
        }

        return json(HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not Found\"}");
    }

    // ─── handlers ───────────────────────────────────────────────────────────

    private FullHttpResponse handleGet(String key) {
        Optional<String> val = cache.get(key);
        if (val.isEmpty()) throw new KeyNotFoundException(key);
        return json(HttpResponseStatus.OK,
                String.format("{\"key\":%s,\"value\":%s}", js(key), js(val.get())));
    }

    private FullHttpResponse handlePut(String body) {
        if (role == ServerRole.REPLICA) {
            return replicaWriteRejected();
        }

        String key    = extractField(body, "key");
        String value  = extractField(body, "value");
        String ttlStr = extractField(body, "ttl");

        if (key == null || value == null) {
            return json(HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"Request body must contain 'key' and 'value'\"}");
        }

        long ttlSeconds = ttlStr != null ? Long.parseLong(ttlStr) : defaultTtlSeconds;
        cache.put(key, value, Duration.ofSeconds(ttlSeconds));

        if (publisher != null) {
            publisher.replicatePut(key, value, ttlSeconds);
        }

        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"OK\",\"key\":%s}", js(key)));
    }

    private FullHttpResponse handleDelete(String key) {
        if (role == ServerRole.REPLICA) {
            return replicaWriteRejected();
        }

        if (!cache.delete(key)) throw new KeyNotFoundException(key);

        if (publisher != null) {
            publisher.replicateDelete(key);
        }

        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"deleted\",\"key\":%s}", js(key)));
    }

    private FullHttpResponse handleExists(String key) {
        return json(HttpResponseStatus.OK,
                String.format("{\"key\":%s,\"exists\":%b}", js(key), cache.exists(key)));
    }

    private FullHttpResponse handleListNodes() {
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"nodes\":%s,\"count\":%d}", jsArray(ids), ids.size()));
    }

    private FullHttpResponse handleAddNode(String nodeId) {
        if (role == ServerRole.REPLICA) return replicaWriteRejected();
        cache.addNode(nodeId);
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"added\",\"nodeId\":%s,\"nodes\":%s}",
                        js(nodeId), jsArray(ids)));
    }

    private FullHttpResponse handleRemoveNode(String nodeId) {
        if (role == ServerRole.REPLICA) return replicaWriteRejected();
        cache.removeNode(nodeId);
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"removed\",\"nodeId\":%s,\"nodes\":%s}",
                        js(nodeId), jsArray(ids)));
    }

    private FullHttpResponse handleHealth() {
        List<String> nodes = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"UP\",\"role\":\"%s\",\"nodes\":%d}",
                        role.name(), nodes.size()));
    }

    private FullHttpResponse replicaWriteRejected() {
        return json(HttpResponseStatus.METHOD_NOT_ALLOWED,
                "{\"error\":\"REPLICA is read-only. Send writes to the PRIMARY.\"}");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    FullHttpResponse json(HttpResponseStatus status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        resp.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
            .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return resp;
    }

    private boolean GET(HttpMethod m)    { return HttpMethod.GET.equals(m); }
    private boolean POST(HttpMethod m)   { return HttpMethod.POST.equals(m); }
    private boolean DELETE(HttpMethod m) { return HttpMethod.DELETE.equals(m); }

    private Matcher match(String regex, String input) {
        return Pattern.compile(regex).matcher(input);
    }

    private String extractField(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private String js(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String jsArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(js(list.get(i)));
        }
        return sb.append("]").toString();
    }

    private String jsonError(String msg) {
        return String.format("{\"error\":%s}", js(msg == null ? "unknown error" : msg));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
