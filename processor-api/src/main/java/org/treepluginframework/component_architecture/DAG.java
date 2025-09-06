package org.treepluginframework.component_architecture;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.*;

public class DAG<T> {
    //IdentityHashMap since I have to check for references instead of .equals() instead.
    private final Map<T, Set<T>> adjList = new IdentityHashMap<>();
    private final Map<T, Set<T>> reverseAdjList = new IdentityHashMap<>();
    private final BiMap<UUID,T> dagUUIDs = HashBiMap.create();

    public void addNode(T node) {
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        if(!adjList.containsKey(node)){
            UUID rand = UUID.randomUUID();
            while(dagUUIDs.containsKey(rand)){
                rand = UUID.randomUUID();
            }
            dagUUIDs.put(rand, node);
        }
        adjList.putIfAbsent(node, new HashSet<>());
        reverseAdjList.putIfAbsent(node, new HashSet<>());
    }

    public void addEdge(T from, T to) {
        if (to == null) {
            throw new IllegalArgumentException("Destination of edge cannot be null");
        }

        if (from != null && createsCycle(from, to)) {
            throw new IllegalArgumentException("Adding edge from " + from + " to " + to + " would create a cycle.");
        }

        addNode(to);

        if (from != null) {
            addNode(from);
            adjList.get(from).add(to);
            reverseAdjList.get(to).add(from);
        }
        // else: root node — no parent edge needed, just leave it in the graph
    }


    public boolean removeEdge(T from, T to) {
        boolean removed = false;
        if (adjList.containsKey(from)) {
            removed |= adjList.get(from).remove(to);
        }
        if (reverseAdjList.containsKey(to)) {
            removed |= reverseAdjList.get(to).remove(from);
        }
        return removed;
    }

    public Set<T> getChildren(T node) {
        return Collections.unmodifiableSet(adjList.getOrDefault(node, Collections.emptySet()));
    }

    public Set<T> getParents(T node) {
        return Collections.unmodifiableSet(reverseAdjList.getOrDefault(node, Collections.emptySet()));
    }

    public boolean removeNode(T node) {
        boolean existed = adjList.containsKey(node) || reverseAdjList.containsKey(node);

        if(dagUUIDs.containsValue(node)){
            UUID uuidToRemove = dagUUIDs.inverse().get(node);
            dagUUIDs.remove(uuidToRemove);
        }

        // Remove all outgoing edges from this node
        Set<T> children = adjList.remove(node);
        if (children != null) {
            for (T child : children) {
                reverseAdjList.get(child).remove(node);
            }
        }

        // Remove all incoming edges to this node
        Set<T> parents = reverseAdjList.remove(node);
        if (parents != null) {
            for (T parent : parents) {
                adjList.get(parent).remove(node);
            }
        }

        return existed;
    }

    private boolean createsCycle(T from, T to) {
        // Check if there is a path from 'to' to 'from'
        Set<T> visited = new HashSet<>();
        Deque<T> stack = new ArrayDeque<>();
        stack.push(to);

        while (!stack.isEmpty()) {
            T current = stack.pop();
            if (current.equals(from)) return true;
            if (visited.add(current)) {
                stack.addAll(adjList.getOrDefault(current, Collections.emptySet()));
            }
        }
        return false;
    }

    public boolean containsNode(T node) {
        return adjList.containsKey(node);
    }

    public Set<T> getAllNodes() {
        return Collections.unmodifiableSet(adjList.keySet());
    }

    public void printGraph() {
        System.out.println("Forward Edges:");
        for (var entry : adjList.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }

        System.out.println("\nReverse Edges:");
        for (var entry : reverseAdjList.entrySet()) {
            System.out.println(entry.getKey() + " <- " + entry.getValue());
        }
    }

    public void printFrom(T node) {
        Set<T> visited = new HashSet<>();
        printFromHelper(node, 0);
    }

    private void printFromHelper(T node, int depth) {
        if (node == null) {
            return;
        }

        // Indent and print
        System.out.println("\t".repeat(depth) + node.toString());

        // Recurse on children (dependencies)
        Set<T> children = adjList.getOrDefault(node, Collections.emptySet());
        for (T child : children) {
            printFromHelper(child, depth+1);
        }
    }

    public List<T> getRoots(){
        List<T> roots = new ArrayList<>();

        for(T node : adjList.keySet()){
            if(!reverseAdjList.containsKey(node) || reverseAdjList.get(node).isEmpty()){
                roots.add(node);
            }
        }
        return roots;
    }
}
