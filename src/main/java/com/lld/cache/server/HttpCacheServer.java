package com.lld.cache.server;

import com.lld.cache.DistributedCache;
import com.lld.cache.config.CacheConfig;
import com.lld.cache.config.ReplicationMode;
import com.lld.cache.eviction.EvictionPolicyType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.time.Duration;

/**
 * Netty-based HTTP server for the DistributedCache.
 *
 * Run from IntelliJ: set main class to com.lld.cache.server.HttpCacheServer
 * Default port: 8080  (override with --port=<n>)
 *
 * REST API
 * --------
 * GET    /cache/{key}          – get value
 * POST   /cache                – put  body: {"key":"k","value":"v","ttl":60}
 * DELETE /cache/{key}          – delete key
 * GET    /cache/{key}/exists   – check existence
 *
 * GET    /nodes                – list nodes
 * POST   /nodes/{nodeId}       – add node
 * DELETE /nodes/{nodeId}       – remove node
 *
 * GET    /health               – health check
 */
public class HttpCacheServer {

    private final DistributedCache<String, String> cache;
    private final int port;

    public HttpCacheServer(DistributedCache<String, String> cache, int port) {
        this.cache = cache;
        this.port = port;
    }

    public void start() throws InterruptedException {
        // Boss accepts incoming connections; worker handles I/O on accepted channels
        EventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                              .addLast(new HttpServerCodec())                  // decode req / encode resp
                              .addLast(new HttpObjectAggregator(512 * 1024))   // aggregate chunked body (max 512 KB)
                              .addLast(new CacheHttpHandler(cache));            // our routing logic
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();

            printBanner();

            // Wait until the server socket is closed
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║      Distributed Cache  (Netty)      ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.printf("Listening on http://localhost:%d%n", port);
        System.out.println("Active nodes: " + cache.getNodeIds());
        System.out.println("Press Ctrl+C to stop.\n");
        System.out.println("Endpoints:");
        System.out.printf("  GET    http://localhost:%d/cache/{key}%n", port);
        System.out.printf("  POST   http://localhost:%d/cache          body: {\"key\":\"k\",\"value\":\"v\",\"ttl\":60}%n", port);
        System.out.printf("  DELETE http://localhost:%d/cache/{key}%n", port);
        System.out.printf("  GET    http://localhost:%d/cache/{key}/exists%n", port);
        System.out.printf("  GET    http://localhost:%d/nodes%n", port);
        System.out.printf("  POST   http://localhost:%d/nodes/{nodeId}%n", port);
        System.out.printf("  DELETE http://localhost:%d/nodes/{nodeId}%n", port);
        System.out.printf("  GET    http://localhost:%d/health%n", port);
    }

    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        CacheConfig config = new CacheConfig.Builder()
                .maxEntriesPerNode(10_000)
                .evictionPolicy(EvictionPolicyType.LRU)
                .replicationFactor(2)
                .replicationMode(ReplicationMode.ASYNC)
                .defaultTtl(Duration.ofHours(1))
                .build();

        DistributedCache<String, String> cache = new DistributedCache<>(config);
        cache.addNode("node-1");
        cache.addNode("node-2");
        cache.addNode("node-3");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down cache...");
            cache.shutdown();
        }));

        new HttpCacheServer(cache, port).start();
    }
}
