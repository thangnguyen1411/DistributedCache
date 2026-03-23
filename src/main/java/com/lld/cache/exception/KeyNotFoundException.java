package com.lld.cache.exception;

public class KeyNotFoundException extends CacheException {

    public KeyNotFoundException(String key) {
        super("Key not found: " + key, "KEY_NOT_FOUND");
    }
}
