package com.lld.cache.exception;

public class CacheException extends RuntimeException {

    private final String errorCode;

    public CacheException(String message) {
        super(message);
        this.errorCode = null;
    }

    public CacheException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
