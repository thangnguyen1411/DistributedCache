package com.lld.cache.eviction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LRUEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Map<K, Node<K>> nodeMap = new HashMap<>();
    private final Node<K> head;
    private final Node<K> tail;

    public LRUEvictionPolicy() {
        head = new Node<>(null);
        tail = new Node<>(null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void keyAccessed(K key) {
        Node<K> node = nodeMap.get(key);
        if (node != null) {
            removeNode(node);
            addToTail(node);
        }
    }

    @Override
    public void keyAdded(K key) {
        Node<K> node = new Node<>(key);
        addToTail(node);
        nodeMap.put(key, node);
    }

    @Override
    public void keyRemoved(K key) {
        Node<K> node = nodeMap.remove(key);
        if (node != null) {
            removeNode(node);
        }
    }

    @Override
    public Optional<K> evict() {
        if (nodeMap.isEmpty()) {
            return Optional.empty();
        }
        Node<K> lru = head.next;
        removeNode(lru);
        nodeMap.remove(lru.key);
        return Optional.of(lru.key);
    }

    @Override
    public int size() {
        return nodeMap.size();
    }

    private void addToTail(Node<K> node) {
        node.prev = tail.prev;
        node.next = tail;
        tail.prev.next = node;
        tail.prev = node;
    }

    private void removeNode(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private static class Node<K> {
        K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) {
            this.key = key;
        }
    }
}
