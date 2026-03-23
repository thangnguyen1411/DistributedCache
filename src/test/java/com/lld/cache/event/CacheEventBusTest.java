package com.lld.cache.event;

import com.lld.cache.model.CacheEvent;
import com.lld.cache.model.CacheEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CacheEventBusTest {

    private CacheEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new CacheEventBus();
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    @Test
    void listenerReceivesPublishedEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<CacheEvent> received = new CopyOnWriteArrayList<>();

        eventBus.register(event -> {
            received.add(event);
            latch.countDown();
        });

        eventBus.publish(new CacheEvent(CacheEventType.PUT, "key1", "node-1"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.size());
        assertEquals(CacheEventType.PUT, received.get(0).type());
        assertEquals("key1", received.get(0).key());
    }

    @Test
    void unregisteredListenerDoesNotReceiveEvents() throws InterruptedException {
        CopyOnWriteArrayList<CacheEvent> received = new CopyOnWriteArrayList<>();
        CacheEventListener listener = received::add;

        eventBus.register(listener);
        eventBus.unregister(listener);

        eventBus.publish(new CacheEvent(CacheEventType.PUT, "key1", "node-1"));

        Thread.sleep(200);
        assertTrue(received.isEmpty());
    }

    @Test
    void multipleListenersReceiveEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        eventBus.register(event -> latch.countDown());
        eventBus.register(event -> latch.countDown());

        eventBus.publish(new CacheEvent(CacheEventType.DELETE, "key1", "node-1"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    void listenerCountTracked() {
        assertEquals(0, eventBus.listenerCount());

        CacheEventListener listener = event -> {};
        eventBus.register(listener);
        assertEquals(1, eventBus.listenerCount());

        eventBus.unregister(listener);
        assertEquals(0, eventBus.listenerCount());
    }
}
