package com.lld.cache.exception;

public class NodeNotFoundException extends CacheException {

    public NodeNotFoundException(String nodeId) {
        super("Node not found: " + nodeId, "NODE_NOT_FOUND");
    }
}
