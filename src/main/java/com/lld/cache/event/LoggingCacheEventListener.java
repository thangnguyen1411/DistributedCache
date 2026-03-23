package com.lld.cache.event;

import com.lld.cache.model.CacheEvent;

import java.util.logging.Logger;

public class LoggingCacheEventListener implements CacheEventListener {

    private static final Logger logger = Logger.getLogger(LoggingCacheEventListener.class.getName());

    @Override
    public void onEvent(CacheEvent event) {
        logger.info(String.format("[%s] key=%s node=%s at=%s",
                event.type(), event.key(), event.nodeId(), event.timestamp()));
    }
}
