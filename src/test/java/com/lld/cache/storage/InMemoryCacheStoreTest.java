package com.lld.cache.storage;

import com.lld.cache.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheStoreTest {

    private InMemoryCacheStore<String, String> store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCacheStore<>();
    }

    @Test
    void putAndGet() {
        CacheEntry<String, String> entry = new CacheEntry<>("key1", "value1");
        store.put("key1", entry);

        assertTrue(store.get("key1").isPresent());
        assertEquals("value1", store.get("key1").get().getValue());
    }

    @Test
    void getMissingKeyReturnsEmpty() {
        assertTrue(store.get("missing").isEmpty());
    }

    @Test
    void removeReturnsEntry() {
        store.put("key1", new CacheEntry<>("key1", "value1"));

        CacheEntry<String, String> removed = store.remove("key1");
        assertNotNull(removed);
        assertEquals("value1", removed.getValue());
        assertTrue(store.get("key1").isEmpty());
    }

    @Test
    void containsKey() {
        store.put("key1", new CacheEntry<>("key1", "value1"));

        assertTrue(store.containsKey("key1"));
        assertFalse(store.containsKey("missing"));
    }

    @Test
    void sizeAndClear() {
        store.put("a", new CacheEntry<>("a", "1"));
        store.put("b", new CacheEntry<>("b", "2"));

        assertEquals(2, store.size());

        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    void keysReturnsAllKeys() {
        store.put("a", new CacheEntry<>("a", "1"));
        store.put("b", new CacheEntry<>("b", "2"));

        assertEquals(2, store.keys().size());
        assertTrue(store.keys().contains("a"));
        assertTrue(store.keys().contains("b"));
    }

    @Test
    void entriesReturnsAllEntries() {
        store.put("a", new CacheEntry<>("a", "1"));
        store.put("b", new CacheEntry<>("b", "2"));

        assertEquals(2, store.entries().size());
    }
}
