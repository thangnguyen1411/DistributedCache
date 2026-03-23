package com.lld.cache.event;

import com.lld.cache.model.CacheEvent;

public interface CacheEventListener {
    void onEvent(CacheEvent event);
}
