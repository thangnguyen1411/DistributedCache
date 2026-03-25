package com.lld.cache.server;

import com.lld.cache.DistributedCache;
import com.lld.cache.config.ConfigLoader;
import com.lld.cache.config.ServerConfig;
import com.lld.cache.config.ServerRole;
import com.lld.cache.replication.PrimaryReplicationPublisher;
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

/**
 * Netty HTTP server — entry point for both PRIMARY and REPLICA instances.
 *
 * Usage:
 *   java -jar distributed-cache.jar                          # uses configuration.yml from classpath
 *   java -jar distributed-cache.jar --config=my-config.yml  # explicit config file
 *
 * To run a replica:
 *   java -jar distributed-cache.jar --config=application-replica.yml
 */
public class HttpCacheServer {

    private final ServerConfig serverConfig;
    private final DistributedCache<String, String> cache;
    private final PrimaryReplicationPublisher publisher;
    private final InternalReplicationHandler internalHandler;

    public HttpCacheServer(ServerConfig serverConfig,
                           DistributedCache<String, String> cache,
                           PrimaryReplicationPublisher publisher,
                           InternalReplicationHandler internalHandler) {
        this.serverConfig = serverConfig;
        this.cache = cache;
        this.publisher = publisher;
        this.internalHandler = internalHandler;
    }

    public void start() throws InterruptedException {
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
                              .addLast(new HttpServerCodec())
                              .addLast(new HttpObjectAggregator(512 * 1024))
                              .addLast(new CacheHttpHandler(
                                      cache,
                                      serverConfig.getRole(),
                                      serverConfig.getDefaultTtlSeconds(),
                                      publisher,
                                      internalHandler));
                        }
                    });

            ChannelFuture future = bootstrap.bind(serverConfig.getPort()).sync();
            printBanner();
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private void printBanner() {
        int port = serverConfig.getPort();
        ServerRole role = serverConfig.getRole();

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.printf( "║   Distributed Cache  %-6s  (Netty)        ║%n", role);
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.printf("Server ID     %s%n", serverConfig.getId());
        System.out.printf("Listening on  http://localhost:%d%n", port);
        System.out.printf("Role          %s%n", role);
        System.out.printf("Partitions    %s%n", cache.getNodeIds());

        if (role == ServerRole.PRIMARY && !serverConfig.getReplicas().isEmpty()) {
            System.out.printf("Replicas      %s%n", serverConfig.getReplicas());
        }
        if (role == ServerRole.REPLICA && serverConfig.getPrimary() != null) {
            System.out.printf("Primary       %s%n", serverConfig.getPrimary());
            System.out.println("Mode          read-only (writes → primary)");
        }

        System.out.println("\nEndpoints:");
        System.out.printf("  GET    http://localhost:%d/cache/{key}%n", port);
        System.out.printf("  POST   http://localhost:%d/cache          body: {\"key\":\"k\",\"value\":\"v\",\"ttl\":60}%n", port);
        System.out.printf("  DELETE http://localhost:%d/cache/{key}%n", port);
        System.out.printf("  GET    http://localhost:%d/cache/{key}/exists%n", port);
        System.out.printf("  GET    http://localhost:%d/nodes%n", port);
        System.out.printf("  GET    http://localhost:%d/health%n", port);
        System.out.println("\nPress Ctrl+C to stop.");
    }

    // ─── main ───────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        // 1. Resolve config path
        String configPath = "application.yml";
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                configPath = arg.substring("--config=".length());
            }
        }

        // 2. Load config
        ServerConfig serverConfig = ConfigLoader.load(configPath);
        System.out.printf("Loaded config: %s  (role=%s, port=%d)%n",
                configPath, serverConfig.getRole(), serverConfig.getPort());

        // 3. Build cache
        DistributedCache<String, String> cache =
                new DistributedCache<>(ConfigLoader.toCacheConfig(serverConfig));
        serverConfig.generateNodeIds().forEach(cache::addNode);

        // 4. Wire replication components
        PrimaryReplicationPublisher publisher = null;
        if (serverConfig.getRole() == ServerRole.PRIMARY && !serverConfig.getReplicas().isEmpty()) {
            publisher = new PrimaryReplicationPublisher(serverConfig.getReplicas());
            System.out.printf("Replication publisher ready → %s%n", serverConfig.getReplicas());
        }

        InternalReplicationHandler internalHandler =
                new InternalReplicationHandler(cache, serverConfig.getDefaultTtlSeconds());

        // 5. Shutdown hook
        final PrimaryReplicationPublisher finalPublisher = publisher;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            if (finalPublisher != null) finalPublisher.shutdown();
            cache.shutdown();
        }));

        // 6. Start
        new HttpCacheServer(serverConfig, cache, publisher, internalHandler).start();
    }
}
