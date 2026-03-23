package com.lld.cache.exception;

public class InvalidConfigException extends CacheException {

    public InvalidConfigException(String message) {
        super(message, "INVALID_CONFIG");
    }
}
