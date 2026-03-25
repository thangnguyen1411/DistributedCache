package com.lld.cache.server;

import com.lld.cache.DistributedCache;
import com.lld.cache.exception.CacheFullException;
import com.lld.cache.exception.KeyNotFoundException;
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
 * Netty channel handler — routes incoming HTTP requests to cache operations.
 */
public class CacheHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final DistributedCache<String, String> cache;

    public CacheHttpHandler(DistributedCache<String, String> cache) {
        this.cache = cache;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String path   = req.uri();
        HttpMethod method = req.method();
        String body   = req.content().toString(StandardCharsets.UTF_8);

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

        // Close connection after response (HTTP/1.0 style — keeps things simple)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    // ─── router ─────────────────────────────────────────────────────────────

    private FullHttpResponse route(HttpMethod method, String path, String body) {

        // GET /health
        if (GET(method) && path.equals("/health")) {
            return handleHealth();
        }

        // GET /nodes
        if (GET(method) && path.equals("/nodes")) {
            return handleListNodes();
        }

        // POST /nodes/{nodeId}
        Matcher m = match("/nodes/(.+)", path);
        if (m.matches()) {
            String nodeId = m.group(1);
            if (POST(method))   return handleAddNode(nodeId);
            if (DELETE(method)) return handleRemoveNode(nodeId);
        }

        // GET /cache/{key}/exists
        m = match("/cache/(.+)/exists", path);
        if (m.matches()) {
            return handleExists(m.group(1));
        }

        // GET|DELETE /cache/{key}
        m = match("/cache/(.+)", path);
        if (m.matches()) {
            String key = m.group(1);
            if (GET(method))    return handleGet(key);
            if (DELETE(method)) return handleDelete(key);
        }

        // POST /cache
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
        String key    = extractField(body, "key");
        String value  = extractField(body, "value");
        String ttlStr = extractField(body, "ttl");

        if (key == null || value == null) {
            return json(HttpResponseStatus.BAD_REQUEST,
                    "{\"error\":\"Request body must contain 'key' and 'value'\"}");
        }

        if (ttlStr != null) {
            cache.put(key, value, Duration.ofSeconds(Long.parseLong(ttlStr)));
        } else {
            cache.put(key, value);
        }
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"OK\",\"key\":%s}", js(key)));
    }

    private FullHttpResponse handleDelete(String key) {
        if (!cache.delete(key)) throw new KeyNotFoundException(key);
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"deleted\",\"key\":%s}", js(key)));
    }

    private FullHttpResponse handleExists(String key) {
        boolean exists = cache.exists(key);
        return json(HttpResponseStatus.OK,
                String.format("{\"key\":%s,\"exists\":%b}", js(key), exists));
    }

    private FullHttpResponse handleListNodes() {
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"nodes\":%s,\"count\":%d}", jsArray(ids), ids.size()));
    }

    private FullHttpResponse handleAddNode(String nodeId) {
        cache.addNode(nodeId);
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"added\",\"nodeId\":%s,\"nodes\":%s}", js(nodeId), jsArray(ids)));
    }

    private FullHttpResponse handleRemoveNode(String nodeId) {
        cache.removeNode(nodeId);
        List<String> ids = cache.getNodeIds();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"removed\",\"nodeId\":%s,\"nodes\":%s}", js(nodeId), jsArray(ids)));
    }

    private FullHttpResponse handleHealth() {
        int count = cache.getNodeIds().size();
        return json(HttpResponseStatus.OK,
                String.format("{\"status\":\"UP\",\"nodes\":%d}", count));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private FullHttpResponse json(HttpResponseStatus status, String body) {
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

    /** Extracts a string or number field from a JSON body. */
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
