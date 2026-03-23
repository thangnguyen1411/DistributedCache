package com.lld.cache.hash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    private ConsistentHashRing ring;

    @BeforeEach
    void setUp() {
        ring = new ConsistentHashRing(150);
    }

    @Test
    void emptyRingReturnsNull() {
        assertNull(ring.getNodeForKey("any-key"));
    }

    @Test
    void singleNodeHandlesAllKeys() {
        ring.addNode("node-1");

        assertEquals("node-1", ring.getNodeForKey("key1"));
        assertEquals("node-1", ring.getNodeForKey("key2"));
        assertEquals("node-1", ring.getNodeForKey("key999"));
    }

    @Test
    void keysDistributeAcrossNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Set<String> assignedNodes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assignedNodes.add(ring.getNodeForKey("key-" + i));
        }

        assertTrue(assignedNodes.size() > 1, "Keys should distribute across multiple nodes");
    }

    @Test
    void removeNodeRedistributesKeys() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        String originalNode = ring.getNodeForKey("test-key");

        ring.removeNode(originalNode);

        String newNode = ring.getNodeForKey("test-key");
        assertNotNull(newNode);
        assertNotEquals(originalNode, newNode);
    }

    @Test
    void replicaNodesAreDistinctFromPrimary() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        String primary = ring.getNodeForKey("test-key");
        List<String> replicas = ring.getReplicaNodesForKey("test-key", 2);

        assertFalse(replicas.contains(primary));
        assertEquals(2, replicas.size());
        assertNotEquals(replicas.get(0), replicas.get(1));
    }

    @Test
    void replicaCountLimitedByAvailableNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");

        List<String> replicas = ring.getReplicaNodesForKey("test-key", 5);

        assertEquals(1, replicas.size()); // only 1 other node available
    }

    @Test
    void virtualNodeCountAffectsRingSize() {
        ConsistentHashRing smallRing = new ConsistentHashRing(10);
        smallRing.addNode("node-1");

        assertEquals(10, smallRing.size());

        ring.addNode("node-1");
        assertEquals(150, ring.size());
    }

    @Test
    void consistentMappingForSameKey() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        String node1 = ring.getNodeForKey("stable-key");
        String node2 = ring.getNodeForKey("stable-key");

        assertEquals(node1, node2);
    }

    @Test
    void getDistinctNodesReturnsAllPhysicalNodes() {
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        List<String> distinct = ring.getDistinctNodes();
        assertEquals(3, distinct.size());
        assertTrue(distinct.contains("node-1"));
        assertTrue(distinct.contains("node-2"));
        assertTrue(distinct.contains("node-3"));
    }
}
