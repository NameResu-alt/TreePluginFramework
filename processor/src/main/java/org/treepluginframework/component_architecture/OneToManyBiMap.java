package org.treepluginframework.component_architecture;

import java.util.*;

public class OneToManyBiMap<K,V>{
    private final Map<K, List<V>> parentsToChildren = new HashMap<>();
    private final Map<V, K> childrenToParent = new HashMap<>();

    /**
     * Associates the given key with the value.
     * Throws if the value is already associated with a different key.
     */
    public void put(K parent, V child) {
        if (childrenToParent.containsKey(child)) {
            //Ensures that each child only has 1 parent.
            throw new IllegalArgumentException("Value is already associated with a key.");
        }
        parentsToChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
        childrenToParent.put(child, parent);
    }

    /**
     * Gets the list of values associated with a key.
     * Returns an empty list if no mapping exists.
     */
    public List<V> getChildrenOfParent(K parent) {
        return parentsToChildren.getOrDefault(parent, Collections.emptyList());
    }

    /**
     * Gets the key associated with a value.
     * Returns null if not mapped.
     */
    public K getParentOfChild(V child) {
        return childrenToParent.get(child);
    }

    public boolean containsParent(K parent) {
        return parentsToChildren.containsKey(parent);
    }

    public boolean containsChild(V child) {
        return childrenToParent.containsKey(child);
    }

    /**
     * Removes a value and its association with its key.
     */
    public void removeChild(V child) {
        K parentOfChild = childrenToParent.remove(child);
        if (parentOfChild != null) {
            List<V> values = parentsToChildren.get(parentOfChild);
            if (values != null) {
                values.remove(child);
                if (values.isEmpty()) {
                    parentsToChildren.remove(parentOfChild);
                }
            }
        }
    }

    /**
     * Removes a key and all its associated values.
     */
    public void removeParent(K parent) {
        List<V> values = parentsToChildren.remove(parent);
        if (values != null) {
            for (V value : values) {
                childrenToParent.remove(value);
            }
        }
    }

    public void removeCompletely(Object x) {
        // Remove as value
        if (childrenToParent.containsKey(x)) {
            removeChild((V) x);
        }

        // Remove as key
        if (parentsToChildren.containsKey(x)) {
            removeParent((K) x);
        }
    }

    public Set<K> parentSet() {
        return parentsToChildren.keySet();
    }

    public Set<V> childSet() {
        return childrenToParent.keySet();
    }

    public void clear() {
        parentsToChildren.clear();
        childrenToParent.clear();
    }

    public boolean isEmpty() {
        return parentsToChildren.isEmpty();
    }

    public int size() {
        return childrenToParent.size(); // total number of mappings (number of values)
    }
}
