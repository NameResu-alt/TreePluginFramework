package org.treepluginframework.component_architecture;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DAG<T> {
    private final Map<T, LinkedHashSet<T>> adjList;
    private final Map<T, LinkedHashSet<T>> reverseAdjList;
    private final BiMap<UUID,T> dagUUIDs = HashBiMap.create();
    private final UUID dagUUID = UUID.randomUUID();

    private DAG(Supplier<Map<T, LinkedHashSet<T>>> mapSupplier) {
        this.adjList = mapSupplier.get();
        this.reverseAdjList = mapSupplier.get();
    }

    /***
     * DAG implementation will use a Identity HashMap for its edges
     * @return
     * @param <T>
     */
    public static <T> DAG<T> identity() {
        return new DAG<>(IdentityHashMap::new);
    }

    /***
     * DAG implementation will use a regular HashMap for its edges
     * @return
     * @param <T>
     */
    public static <T> DAG<T> regular() {
        return new DAG<>(HashMap::new);
    }

    /***
     * DAG implementation will use a Concurrent HashMap for its edges
     * @return
     * @param <T>
     */

    public static <T> DAG<T> concurrent() {
        return new DAG<>(ConcurrentHashMap::new);
    }

    public UUID getDagUUID(){
        return this.dagUUID;
    }

    public void addNode(T node) {
        if (node == null) throw new IllegalArgumentException("Node cannot be null");
        if(!adjList.containsKey(node)){
            UUID rand = UUID.randomUUID();
            while(dagUUIDs.containsKey(rand)){
                rand = UUID.randomUUID();
            }
            dagUUIDs.put(rand, node);
        }
        adjList.putIfAbsent(node, new LinkedHashSet<>());
        reverseAdjList.putIfAbsent(node, new LinkedHashSet<>());
    }

    /***
     * Add an edge to the DAG. If to is null, or if the edge would create a cycle, throws an exception.
     * @param from
     * @param to
     * @return True if the edge was added, false if edge already exists.
     */
    public boolean addEdge(T from, T to) {
        if (to == null) {
            throw new IllegalArgumentException("Destination of edge cannot be null");
        }

        if (from != null && createsCycle(from, to)) {
            throw new IllegalArgumentException("Adding edge from " + from + " to " + to + " would create a cycle.");
        }

        if(adjList.containsKey(from) && adjList.get(from).contains(to)){
            return false;
        }

        addNode(to);

        if (from != null) {
            addNode(from);
            adjList.get(from).add(to);
            reverseAdjList.get(to).add(from);
        }

        return true;
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

    public LinkedHashSet<T> getChildren(T node) {
        return new LinkedHashSet<>(adjList.getOrDefault(node, new LinkedHashSet<>()));
    }

    public LinkedHashSet<T> getParents(T node) {
        return new LinkedHashSet<>(reverseAdjList.getOrDefault(node, new LinkedHashSet<>()));
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
                stack.addAll(adjList.getOrDefault(current, new LinkedHashSet<>()));
            }
        }
        return false;
    }

    public boolean containsNode(T node) {
        return adjList.containsKey(node);
    }

    public HashMap<UUID,T> getAllNodes() {
        HashMap<UUID,T> all = new HashMap<>();

        for(T key : adjList.keySet()){
            all.put(getNodeUUID(key), key);
        }

        return all;
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
        Set<T> children = adjList.getOrDefault(node, new LinkedHashSet<>());
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

    public List<UUID> getRootUUIDs(){
        List<T> roots = this.getRoots();
        List<UUID> uuids = new ArrayList<>();

        roots.forEach(k->uuids.add(getNodeUUID(k)));

        return uuids;
    }

    public T getNode(UUID nodeUUID){
        return dagUUIDs.getOrDefault(nodeUUID, null);
    }

    public UUID getNodeUUID(T node){
        return dagUUIDs.inverse().getOrDefault(node,null);
    }

    public Map<UUID,LinkedHashSet<UUID>> getAllEdgesInUUID(){
        Map<UUID,LinkedHashSet<UUID>> map = new HashMap<>();
        for(T key : adjList.keySet()){
            UUID nodeUUID = getNodeUUID(key);

            LinkedHashSet<T> children = adjList.get(key);

            LinkedHashSet<UUID> childrenUUID = new LinkedHashSet<>();

            children.forEach(k->childrenUUID.add(getNodeUUID(k)));

            map.put(nodeUUID, childrenUUID);
        }
        return map;
    }

    //If any one of those maps is empty, the dag is empty.
    public boolean isEmpty(){
        return dagUUIDs.isEmpty();
    }
}
