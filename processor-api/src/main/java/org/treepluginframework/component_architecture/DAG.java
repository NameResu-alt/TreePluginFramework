package org.treepluginframework.component_architecture;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class DAG<T> {
    private final UUID dagUUID = UUID.randomUUID();

    private Map<UUID, Node> graph;
    private NodeRepository<T> nodeRepository = null;
    private final List<DAGListener<T>> listeners = new CopyOnWriteArrayList<>();
    /**
     * <ul>
     * <li>Version increase per operation that changes the dag.</li>
     * <li>Adding an edge to the DAG increases version by 1, even though it may add 2 new nodes, and an edge.</li>
     * <li>Removing a node from the DAG increases version by 1, even though it may remove many edges.</li>
     * </ul>
     */
    private long version = 0;

    public DAG(){
        this(Mode.REGULAR);
        this.graph = new HashMap<>();
    }

    public DAG(Mode mode){
        if(mode == null) throw new NullPointerException("Mode for DAG cannot be Null");

        boolean concurrent = (mode == Mode.CONCURRENT_IDENTITY || mode == Mode.CONCURRENT_REGULAR);
        graph = (concurrent) ? new ConcurrentHashMap<>() : new HashMap<>();

        System.out.println("Graph Type: " + graph.getClass().getCanonicalName());
        switch(mode){
            case REGULAR -> {
                nodeRepository = new RegularNodeRepository();
            }
            case IDENTITY -> {
                nodeRepository = new IdentityNodeRepository();
            }
            case CONCURRENT_REGULAR -> {
                nodeRepository = new ConcurrentRegularNodeRepository();
            }
            case CONCURRENT_IDENTITY -> {
                nodeRepository = new ConcurrentIdentityNodeRepository();
            }
        }
    }

    public DAG(Mode mode, Function<T,UUID> newNodeUUIDAssignRule){
        this(mode);

        boolean concurrent = (mode == Mode.CONCURRENT_IDENTITY || mode == Mode.CONCURRENT_REGULAR);
        graph = (concurrent) ? new ConcurrentHashMap<>() : new HashMap<>();

        switch(mode){
            case REGULAR -> {
                nodeRepository = new RegularNodeRepository(newNodeUUIDAssignRule);
            }
            case IDENTITY -> {
                nodeRepository = new IdentityNodeRepository(newNodeUUIDAssignRule);
            }
            case CONCURRENT_REGULAR -> {
                nodeRepository = new ConcurrentRegularNodeRepository(newNodeUUIDAssignRule);
            }
            case CONCURRENT_IDENTITY -> {
                nodeRepository = new ConcurrentIdentityNodeRepository(newNodeUUIDAssignRule);
            }
        }
    }

    public DAG(Mode mode, Function<T,UUID> newNodeUUIDAssignRule, long startingVersion){
        this(mode,newNodeUUIDAssignRule);
        this.version = startingVersion;
    }

    public DAG(Function<T,UUID> newNodeUUIDAssignRule){
        this(Mode.REGULAR, newNodeUUIDAssignRule);
    }

    public DAG(Function<T,UUID> newNodeUUIDAssignRule, long startingVersion){
        this(Mode.REGULAR, newNodeUUIDAssignRule);
        this.version = startingVersion;
    }

    public DAG(long startingVersion){
        this(Mode.REGULAR);
        this.version = startingVersion;
    }


    public long getVersion(){return this.version;}

    public UUID getDagUUID(){
        return this.dagUUID;
    }

    /**
     * Add a node to the DAG, and increase DAG version if successful.
     * @param node The node to add to the DAG; must not be null.
     * @return Returns true if node was added. Returns false if node was not added, or already existed within DAG.
     * @throws NullPointerException if newNode is null
     */
    public boolean addNode(T node) {
        boolean changed = addNodeInternal(node);
        if(changed){
            version++;
            notifyListeners(new NodeAdded<>(node));
        }
        return changed;
    }

    /**
     * Add a node to the DAG without increasing the DAG version. For internal use only.
     * @param node The node to add to the DAG
     * @return Returns true if node was added. Returns false if node was not added, or already existed within DAG.
     * @throws NullPointerException if newNode is null
     */
    private boolean addNodeInternal(T node){
        if(node == null) throw new IllegalArgumentException("Node cannot be null");
        if(nodeRepository.containsObject(node)) return false;

        nodeRepository.addObject(node);
        UUID nodeUUID = nodeRepository.getObjectUUID(node);

        graph.put(nodeUUID, new Node(nodeUUID));
        return true; // node added
    }

    /**
     * Add an edge to the DAG. If to is null, or if the edge would create a cycle, throws an exception.
     * @param from The starting point of the edge
     * @param to The ending point of the edge
     * @return True if the edge was added, false if edge already exists.
     */
    public boolean addEdge(T from, T to) {
        if (to == null) throw new IllegalArgumentException("Destination of edge cannot be null");
        if (from != null && from.equals(to))
            throw new IllegalArgumentException("Self-loops are not allowed in a DAG: " + to);
        if (from != null && createsCycle(from, to))
            throw new IllegalArgumentException("Adding edge from " + from + " to " + to + " would create a cycle.");

        boolean changed = addNodeInternal(to);
        if (from == null) {
            if (changed){
                version++;
                //I could technically call for addNode explicitly, but I think
                //Saying that it was an addEdge call that did this is okay.
                notifyListeners(new EdgeAdded<>(null,to));
            }
            return true;
        }

        changed |= addNodeInternal(from);
        //If you added a new edge, increase version

        UUID fromUUID = getNodeUUID(from);
        UUID toUUID = getNodeUUID(to);

        if(fromUUID != null) {
            Node parent = graph.get(fromUUID);
            Node child = graph.get(toUUID);
            if (!parent.hasChild(toUUID))
            {
                parent.addChild(child.id);
                child.addParent(parent.id);
                changed = true;
            }
        }

        //Only increase once per operation.
        if (changed){
            version++;

            notifyListeners(new EdgeAdded<>(from,to));
        }
        return true;
    }

    /**
     * Remove a node from the DAG.
     * @param node The node to remove
     * @return Returns true if node was removed, false otherwise
     */
    public boolean removeNode(T node) {
        if(!nodeRepository.containsObject(node)) return false;
        return removeNodeByUUID(nodeRepository.getObjectUUID(node));
    }

    /**
     * Replaces a Node in the DAG with another node.
     * <br>
     * If current DAG is an identity version, this method will not do anything.
     * @param original The node that
     * @param newNode The node to be replaced with. Can't be null.
     * @param shouldUpdateVersion Whether DAG version should be updated upon success
     * @return Returns true if node was replaced, false otherwise.
     */
    public boolean replaceNode(T original, T newNode, boolean shouldUpdateVersion){
        boolean changeOccured = nodeRepository.replaceObject(original, newNode);
        if(changeOccured && shouldUpdateVersion){
            version += 1;
            notifyListeners(new NodeReplaced<>(nodeRepository.getObjectUUID(original), original, newNode));
        }
        return changeOccured;
    }

    /**
     * Replaces a Node in the DAG with another node. It looks within the DAG to see if it can find a node that matches nodeUUID and replaces it.
     * <br>
     * Increases version if shouldUpdateVersion is true.
     * <br>
     * If current DAG is an identity version, this method will not do anything.
     * @param nodeUUID The UUID of the node to be replaced
     * @param newNode The node to be replaced with. Can't be null.
     * @param shouldUpdateVersion Whether DAG version should be updated upon success
     * @return Returns true if node was replaced, false otherwise
     */
    public boolean replaceNode(UUID nodeUUID, T newNode, boolean shouldUpdateVersion){
        T originalNode = nodeRepository.getObjectByUUID(nodeUUID);
        boolean changeOccurred = nodeRepository.replaceObjectByUUID(nodeUUID, newNode);
        if(changeOccurred && shouldUpdateVersion){
            version += 1;
            notifyListeners(new NodeReplaced<>(nodeUUID, originalNode, newNode));
        }
        return changeOccurred;
    }

    /**
     * Removes a node from the DAG by its UUID. Increases version if successful.
     * @param uuid UUID of the node in the DAG
     * @return Returns true if the node was removed, false otherwise.
     */
    public boolean removeNodeByUUID(UUID uuid){
        if(!nodeRepository.containsUUID(uuid)) return false;
        T obj = nodeRepository.getObjectByUUID(uuid);
        nodeRepository.removeObjectByUUID(uuid);

        Node removedNode = graph.remove(uuid);
        for(UUID child : removedNode.children){
            graph.get(child).removeParent(uuid);
        }

        version += 1;
        notifyListeners(new NodeRemoved<>(obj, uuid));

        return true;
    }

    /**
     * Remove an edge from the DAG.
     * @param from The starting point of the edge.
     * @param to The ending point of the edge
     * @return Returns true if edge removed, false otherwise.
     */
    public boolean removeEdge(T from, T to) {
        //I could throw an exception if the nodes weren't in teh tree, doesn't sound good though.
        if(!nodeRepository.containsObject(from) || !nodeRepository.containsObject(to)) return false;

        UUID fromUUID = nodeRepository.getObjectUUID(from);
        UUID toUUID = nodeRepository.getObjectUUID(to);

        Node fromNode = graph.get(fromUUID);
        Node toNode = graph.get(toUUID);

        if(!fromNode.hasChild(toUUID)) return false;

        fromNode.removeChild(toUUID);
        toNode.removeParent(fromUUID);

        version += 1;

        notifyListeners(new EdgeRemoved<>(from, fromUUID, to, toUUID));

        return true;
    }

    public LinkedHashSet<T> getChildren(UUID nodeUUID){
        if(nodeUUID == null || !graph.containsKey(nodeUUID)) return new LinkedHashSet<>();
        Node parent = graph.get(nodeUUID);
        LinkedHashSet<T> children = new LinkedHashSet<>();

        for(UUID childUUID : parent.children){
            children.add(getNodeByUUID(childUUID));
        }

        return children;
    }

    public LinkedHashSet<T> getChildren(T node) {
        return getChildren(getNodeUUID(node));
    }

    public LinkedHashSet<T> getParents(UUID nodeUUID){
        if(nodeUUID == null || !graph.containsKey(nodeUUID)) return new LinkedHashSet<>();
        Node child = graph.get(nodeUUID);
        LinkedHashSet<T> parents = new LinkedHashSet<>();

        for(UUID parentUUID : child.parents){
            parents.add(getNodeByUUID(parentUUID));
        }
        return parents;
    }

    public LinkedHashSet<T> getParents(T node) {
        return getParents(getNodeUUID(node));
    }


    private boolean createsCycle(T from, T to) {
        if(from == null) return false;
        if(!nodeRepository.containsObject(from) || !nodeRepository.containsObject(to)) return false;

        UUID fromUUID = getNodeUUID(from);
        UUID toUUID = getNodeUUID(to);

        // Check if there is a path from 'to' to 'from'
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(toUUID);

        while (!stack.isEmpty()) {
            UUID currentUUID = stack.pop();
            if (currentUUID.equals(fromUUID)) return true;
            if (visited.add(currentUUID)) {
                stack.addAll(graph.get(currentUUID).children);
            }
        }
        return false;
    }

    public boolean containsNode(T node){return nodeRepository.containsObject(node);}


    public HashMap<UUID,T> getAllNodes() {
        return nodeRepository.getAllObjectsAndUUIDs();
    }

    public void printGraph(){
        HashMap<UUID,T> objs = nodeRepository.getAllObjectsAndUUIDs();

        int totalEdgeCount = 0;
        System.out.println("V"+ getVersion() + " DAG Graph Information for " + dagUUID);
        System.out.println("\tTotal Node Count: " + objs.size());
        for(UUID nodeUUID : objs.keySet()){
            System.out.println("\t"+ nodeUUID + " - " + objs.get(nodeUUID));

            for(UUID childUUID : graph.get(nodeUUID).children){
                totalEdgeCount += 1;
                System.out.println("\t\t"+childUUID + " - " + objs.get(childUUID));
            }
        }
        System.out.println("\tTotal Edge Count: " + totalEdgeCount);
    }

    public void printFrom(T node) {
        Set<T> visited = new HashSet<>();
        printFromHelper(node, 0);
    }

    private void printFromHelper(T node, int depth) {
        if (node == null) {
            return;
        }
        if(!nodeRepository.containsObject(node)){
            System.out.println("DAG " + dagUUID + " does not have the node " + node);
            return;
        }
        UUID nodeUUID = getNodeUUID(node);
        // Indent and print
        System.out.println("\t".repeat(depth) + nodeUUID + " - " + node.toString());

        LinkedHashSet<T> currentChildren = getChildren(nodeUUID);

        for(T child : currentChildren){
            printFromHelper(child, depth+1);
        }
    }

    public List<T> getRoots(){
        List<T> roots =  new ArrayList<>();
        for(UUID nodeUUID : graph.keySet()){
            if(!graph.get(nodeUUID).parents.isEmpty()) continue;
            roots.add(getNodeByUUID(nodeUUID));
        }

        return roots;
    }

    public List<UUID> getRootUUIDs(){
        List<UUID> uuids = new ArrayList<>();
        getRoots().forEach(k->uuids.add(getNodeUUID(k)));
        return uuids;
    }

    public T getNodeByUUID(UUID nodeUUID){
        return nodeRepository.getObjectByUUID(nodeUUID);
    }

    /**
     * If DAG is not an Identity DAG, it'll return whatever node in the DAG is .equals() to the node provided.
     * <br>
     * If DAG is an Identity DAG, it'll just return node itself.
     * @param node The node to check with
     * @return Corresponding node to provided node
     */
    public T getCorrespondingNodeReference(T node){
        return nodeRepository.getObjectByUUID(nodeRepository.getObjectUUID(node));
    }

    public UUID getNodeUUID(T node){
        return nodeRepository.getObjectUUID(node);
    }

    public Map<UUID, LinkedHashSet<UUID>> getAllEdgesInUUID(){
        Map<UUID, LinkedHashSet<UUID>> map = new HashMap<>();

        for(Node n : graph.values()){
            map.put(n.getNodeUUID(), new LinkedHashSet<>(n.children));
        }

        return map;
    }

    public boolean isEmpty(){
        return graph.isEmpty();
    }

    // Event types
    sealed interface DAGEvent<T> {}
    record NodeAdded<T>(T node) implements DAGEvent<T> {}
    record EdgeAdded<T>(T from, T to) implements DAGEvent<T> {}
    record NodeRemoved<T>(T node, UUID nodeRemovedUUID) implements DAGEvent<T> {}
    record EdgeRemoved<T>(T from,UUID fromUUID, T to, UUID toUUID) implements DAGEvent<T> {}
    record NodeReplaced<T>(UUID nodeUUID, T oldNode, T newNode) implements DAGEvent<T>{};

    // Listener interface
    public interface DAGListener<T> {
        void onEvent(DAGEvent<T> event, UUID dagUUID, long version);
    }

    public void addListener(DAGListener<T> listener) { listeners.add(listener); }
    public void removeListener(DAGListener<T> listener) { listeners.remove(listener); }

    private void notifyListeners(DAGEvent<T> event) {
        for (DAGListener<T> listener : listeners) {
            try {
                listener.onEvent(event, this.dagUUID, this.version);
            } catch (Exception e) {
                // Option 1: log and continue
                System.err.println("Listener threw an exception: " + e);
                e.printStackTrace();
            }
        }
    }

    private final class Node {
        private final UUID id;
        private final Set<UUID> parents = new LinkedHashSet<>();
        private final Set<UUID> children = new LinkedHashSet<>();

        public Node(UUID id) {
            this.id = id;
        }
        public UUID getNodeUUID(){return this.id;}
        public boolean hasParent(UUID parentUUID){return parents.contains(parentUUID);}
        public boolean hasChild(UUID childUUID){return children.contains(childUUID);}

        public boolean addParent(UUID parentUUID){
            if(parents.contains(parentUUID)) return false;
            parents.add(parentUUID);
            return true;
        }

        public boolean addChild(UUID childUUID){
            if(children.contains(childUUID)) return false;
            children.add(childUUID);
            return true;
        }

        public boolean removeParent(UUID parentUUID){
            if(!parents.contains(parentUUID)) return false;
            parents.remove(parentUUID);
            return true;
        }

        public boolean removeChild(UUID childUUID){
            if(!children.contains(childUUID)) return false;
            children.remove(childUUID);
            return true;
        }

    }


    // -------------------------------------------------------------------
    // Inner resolver interface + implementations
    // -------------------------------------------------------------------

    private abstract class NodeRepository<T>{
        protected Function<T,UUID> newNodeUUIDAssignRule = (node) -> UUID.randomUUID();

        public NodeRepository(){}

        public NodeRepository(Function<T,UUID> newNodeUUIDAssignRule){
            this.newNodeUUIDAssignRule = newNodeUUIDAssignRule;
        }

        public abstract boolean containsUUID(UUID uuid);
        public abstract boolean containsObject(T obj);
        public abstract T getObjectByUUID(UUID objectUUID);
        public abstract UUID getObjectUUID(T obj);
        public abstract boolean addObject(T obj);
        public abstract boolean removeObject(T obj);
        public abstract boolean removeObjectByUUID(UUID objectUUID);
        public abstract boolean replaceObject(T obj, T replacement);
        public abstract boolean replaceObjectByUUID(UUID objectUUID, T replacement);
        public abstract List<T> getAllObjects();
        public abstract List<UUID> getAllUUIDs();
        public abstract HashMap<UUID,T> getAllObjectsAndUUIDs();
    }

    private class RegularNodeRepository extends NodeRepository<T>{
        protected final BiMap<UUID, T> map = HashBiMap.create();

        public RegularNodeRepository(){

        }

        public RegularNodeRepository(Function<T, UUID> newNodeUUIDAssignRule) {
            super(newNodeUUIDAssignRule);
        }

        @Override
        public boolean containsUUID(UUID uuid){
            return map.containsKey(uuid);
        }

        @Override
        public boolean containsObject(T obj) {
            return map.containsValue(obj);
        }

        @Override
        public T getObjectByUUID(UUID objectUUID) {
            return map.getOrDefault(objectUUID, null);
        }

        @Override
        public UUID getObjectUUID(T obj) {
            return map.inverse().getOrDefault(obj, null);
        }

        @Override
        public boolean addObject(T obj) {
            if(containsObject(obj)) return false;
            UUID rand = newNodeUUIDAssignRule.apply(obj);
            int attempts = 0;
            while(map.containsKey(rand) && attempts < 2){
                rand = newNodeUUIDAssignRule.apply(obj);
                attempts++;
            }
            if (attempts == 2)
                throw new IllegalStateException("Custom UUID assignment rule leads to collisions for node: " + obj);
            map.put(rand,obj);
            return true;
        }

        @Override
        public boolean removeObject(T obj) {
            if(!this.map.inverse().containsKey(obj)) return false;
            return removeObjectByUUID(map.inverse().get(obj));
        }

        @Override
        public boolean removeObjectByUUID(UUID objectUUID) {
            if(!this.map.containsKey(objectUUID)) return false;
            this.map.remove(objectUUID);
            return true;
        }

        @Override
        public boolean replaceObject(T obj, T replacement) {
            if(!this.containsObject(obj)) return false;

            if(obj == null){
                throw new NullPointerException("Obj is null, and won't replace " + replacement);
            }

            if(replacement == null){
                throw new NullPointerException("Cannot replace " + obj + " with null");
            }

            UUID objectID = this.getObjectUUID(obj);
            map.put(objectID,replacement);

            return true;
        }

        @Override
        public boolean replaceObjectByUUID(UUID objectUUID, T replacement) {
            if(objectUUID == null){
                throw new NullPointerException("Cannot replace an object with a null UUID: " + replacement);
            }

            if(replacement == null){
                throw new NullPointerException("Cannot replace object with UUID of " + objectUUID + " with null");
            }

            if(!map.containsKey(objectUUID)) return false;
            //I thought about doing an equals check to see if it already exists, but this is a regular DAG
            //So replacing by something that matches equals() is fine.

            map.put(objectUUID, replacement);
            return true;
        }

        @Override
        public List<T> getAllObjects() {
            return new ArrayList<>(map.values());
        }

        @Override
        public List<UUID> getAllUUIDs() {
            return new ArrayList<>(map.keySet());
        }

        @Override
        public HashMap<UUID, T> getAllObjectsAndUUIDs() {
            return new HashMap<>(map);
        }
    }

    private class IdentityNodeRepository extends NodeRepository<T> {

        // UUID -> object map (normal, for reverse lookup)
        private final Map<UUID, T> uuidToObject = new HashMap<>();
        // Object -> UUID map (identity semantics)
        private final IdentityHashMap<T, UUID> objectToUUID = new IdentityHashMap<>();

        public IdentityNodeRepository(){

        }

        public IdentityNodeRepository(Function<T,UUID> nodeAssignRule){
            super(nodeAssignRule);
        }

        @Override
        public boolean containsUUID(UUID uuid) {
            return uuidToObject.containsKey(uuid);
        }

        @Override
        public boolean containsObject(T obj) {
            return objectToUUID.containsKey(obj);
        }

        @Override
        public T getObjectByUUID(UUID objectUUID) {
            return uuidToObject.get(objectUUID);
        }

        @Override
        public UUID getObjectUUID(T obj) {
            return objectToUUID.get(obj);
        }

        @Override
        public boolean addObject(T obj) {
            if (containsObject(obj)) return false;

            UUID rand = newNodeUUIDAssignRule.apply(obj);
            int attempts = 0;
            while (uuidToObject.containsKey(rand) && attempts < 5) {
                rand = newNodeUUIDAssignRule.apply(obj);
                attempts++;
            }
            if (attempts == 5)
                throw new IllegalStateException("Custom UUID assignment rule leads to collisions for node: " + obj);

            uuidToObject.put(rand, obj);
            objectToUUID.put(obj, rand);
            System.out.println("Added a New Node!: " + obj + " Size: " + objectToUUID.size());
            return true;
        }

        @Override
        public boolean removeObject(T obj) {
            UUID id = objectToUUID.remove(obj);
            if (id == null) return false;
            uuidToObject.remove(id);
            return true;
        }

        @Override
        public boolean removeObjectByUUID(UUID objectUUID) {
            T obj = uuidToObject.remove(objectUUID);
            if (obj == null) return false;
            objectToUUID.remove(obj);
            return true;
        }

        @Override
        public boolean replaceObject(T obj, T replacement) {
            return false;
        }

        @Override
        public boolean replaceObjectByUUID(UUID objectUUID, T replacement) {
            return false;
        }

        @Override
        public List<T> getAllObjects() {
            return new ArrayList<>(objectToUUID.keySet());
        }

        @Override
        public List<UUID> getAllUUIDs() {
            return new ArrayList<>(objectToUUID.values());
        }

        @Override
        public HashMap<UUID, T> getAllObjectsAndUUIDs() {
            return new HashMap<>(uuidToObject);
        }
    }

    private class ConcurrentRegularNodeRepository extends RegularNodeRepository {
        private final Object lock = new Object();

        public ConcurrentRegularNodeRepository(){

        }

        public ConcurrentRegularNodeRepository(Function<T, UUID> newNodeUUIDAssignRule) {
            super(newNodeUUIDAssignRule);
        }

        @Override
        public boolean containsUUID(UUID uuid) {
            synchronized (lock) {
                return super.containsUUID(uuid);
            }
        }

        @Override
        public boolean containsObject(T obj) {
            synchronized (lock) {
                return super.containsObject(obj);
            }
        }

        @Override
        public T getObjectByUUID(UUID objectUUID) {
            synchronized (lock) {
                return super.getObjectByUUID(objectUUID);
            }
        }

        @Override
        public UUID getObjectUUID(T obj) {
            synchronized (lock) {
                return super.getObjectUUID(obj);
            }
        }

        @Override
        public boolean addObject(T obj) {
            synchronized (lock) {
                return super.addObject(obj);
            }
        }

        @Override
        public boolean removeObject(T obj) {
            synchronized (lock) {
                return super.removeObject(obj);
            }
        }

        @Override
        public boolean removeObjectByUUID(UUID objectUUID) {
            synchronized (lock) {
                return super.removeObjectByUUID(objectUUID);
            }
        }

        @Override
        public List<T> getAllObjects() {
            synchronized (lock){
                return super.getAllObjects();
            }
        }

        @Override
        public List<UUID> getAllUUIDs() {
            synchronized (lock){
                return super.getAllUUIDs();
            }
        }

        @Override
        public HashMap<UUID, T> getAllObjectsAndUUIDs() {
            synchronized (lock){
                return super.getAllObjectsAndUUIDs();
            }
        }
    }

    private class ConcurrentIdentityNodeRepository extends IdentityNodeRepository {
        private final Object lock = new Object();

        public ConcurrentIdentityNodeRepository(){

        }

        public ConcurrentIdentityNodeRepository(Function<T, UUID> newNodeUUIDAssignRule) {
            super(newNodeUUIDAssignRule);
        }

        @Override
        public boolean containsUUID(UUID uuid) {
            synchronized (lock) { return super.containsUUID(uuid); }
        }

        @Override
        public boolean containsObject(T obj) {
            synchronized (lock) { return super.containsObject(obj); }
        }

        @Override
        public T getObjectByUUID(UUID objectUUID) {
            synchronized (lock) { return super.getObjectByUUID(objectUUID); }
        }

        @Override
        public UUID getObjectUUID(T obj) {
            synchronized (lock) { return super.getObjectUUID(obj); }
        }

        @Override
        public boolean addObject(T obj) {
            synchronized (lock) { return super.addObject(obj); }
        }

        @Override
        public boolean removeObject(T obj) {
            synchronized (lock) { return super.removeObject(obj); }
        }

        @Override
        public boolean removeObjectByUUID(UUID objectUUID) {
            synchronized (lock) { return super.removeObjectByUUID(objectUUID); }
        }

        @Override
        public boolean replaceObject(T obj, T replacement) {
            return false;
        }

        @Override
        public boolean replaceObjectByUUID(UUID objectUUID, T replacement) {
            return false;
        }

        @Override
        public List<T> getAllObjects() {
            synchronized (lock){
                return super.getAllObjects();
            }
        }

        @Override
        public List<UUID> getAllUUIDs() {
            synchronized (lock){
                return super.getAllUUIDs();
            }
        }

        @Override
        public HashMap<UUID, T> getAllObjectsAndUUIDs() {
            synchronized (lock){
                return super.getAllObjectsAndUUIDs();
            }
        }

    }

    public enum Mode {
        REGULAR,
        IDENTITY,
        CONCURRENT_REGULAR,
        CONCURRENT_IDENTITY
    }

}
