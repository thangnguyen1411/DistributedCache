package com.lld.cache.config;

public class NodeAddress {

    private final String host;
    private final int port;

    public NodeAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() { return host; }
    public int getPort()    { return port; }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
