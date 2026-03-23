package com.lld.cache.exception;

public class CacheFullException extends CacheException {

    public CacheFullException(String nodeId) {
        super("Cache node is full: " + nodeId, "CACHE_FULL");
    }
}
