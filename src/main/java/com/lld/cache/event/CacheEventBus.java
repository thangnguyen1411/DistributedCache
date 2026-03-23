package com.lld.cache.event;

import com.lld.cache.model.CacheEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheEventBus {

    private final List<CacheEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor;

    public CacheEventBus() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cache-event-bus");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(CacheEventListener listener) {
        listeners.add(listener);
    }

    public void unregister(CacheEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(CacheEvent event) {
        for (CacheEventListener listener : listeners) {
            executor.submit(() -> listener.onEvent(event));
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public int listenerCount() {
        return listeners.size();
    }
}
