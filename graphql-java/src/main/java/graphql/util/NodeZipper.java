package graphql.util;

import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;

@PublicApi
public class NodeZipper<T> {


    public enum ModificationType {
        REPLACE,
        DELETE,
        INSERT_AFTER,
        INSERT_BEFORE
    }

    private final T curNode;
    private final NodeAdapter<T> nodeAdapter;
    // reverse: the breadCrumbs start from curNode upwards
    private final List<Breadcrumb<T>> breadcrumbs;

    private final ModificationType modificationType;


    public NodeZipper(T curNode, List<Breadcrumb<T>> breadcrumbs, NodeAdapter<T> nodeAdapter) {
        this(curNode, breadcrumbs, nodeAdapter, ModificationType.REPLACE);
    }

    public NodeZipper(T curNode, List<Breadcrumb<T>> breadcrumbs, NodeAdapter<T> nodeAdapter, ModificationType modificationType) {
        this.curNode = assertNotNull(curNode);
        this.breadcrumbs = Collections.unmodifiableList(new ArrayList<>(assertNotNull(breadcrumbs)));
        this.nodeAdapter = nodeAdapter;
        this.modificationType = modificationType;
    }

    public ModificationType getModificationType() {
        return modificationType;
    }

    public T getCurNode() {
        return curNode;
    }

    public List<Breadcrumb<T>> getBreadcrumbs() {
        return breadcrumbs;
    }

    public T getParent() {
        return breadcrumbs.get(0).getNode();
    }

    public static <T> NodeZipper<T> rootZipper(T rootNode, NodeAdapter<T> nodeAdapter) {
        return new NodeZipper<T>(rootNode, new ArrayList<>(), nodeAdapter);
    }

    public NodeZipper<T> modifyNode(Function<T, T> transform) {
        return new NodeZipper<T>(transform.apply(curNode), breadcrumbs, nodeAdapter, this.modificationType);
    }

    public NodeZipper<T> deleteNode() {
        return new NodeZipper<T>(this.curNode, breadcrumbs, nodeAdapter, ModificationType.DELETE);
    }

    public NodeZipper<T> insertAfter(T toInsertAfter) {
        return new NodeZipper<T>(toInsertAfter, breadcrumbs, nodeAdapter, ModificationType.INSERT_AFTER);
    }

    public NodeZipper<T> insertBefore(T toInsertBefore) {
        return new NodeZipper<T>(toInsertBefore, breadcrumbs, nodeAdapter, ModificationType.INSERT_BEFORE);
    }

    public NodeZipper<T> withNewNode(T newNode) {
        return new NodeZipper<T>(newNode, breadcrumbs, nodeAdapter, this.modificationType);
    }

    public NodeZipper<T> moveUp() {
        T node = getParent();
        List<Breadcrumb<T>> newBreadcrumbs = breadcrumbs.subList(1, breadcrumbs.size());
        return new NodeZipper<>(node, newBreadcrumbs, nodeAdapter, this.modificationType);
    }


    /**
     * @return null if it is the root node and marked as deleted, otherwise never null
     */
    public T toRoot() {
        if (breadcrumbs.size() == 0 && modificationType != ModificationType.DELETE) {
            return this.curNode;
        }
        if (breadcrumbs.size() == 0 && modificationType == ModificationType.DELETE) {
            return null;
        }
        T curNode = this.curNode;

        Breadcrumb<T> firstBreadcrumb = breadcrumbs.get(0);
        Map<String, List<T>> childrenForParent = new HashMap<>(nodeAdapter.getNamedChildren(firstBreadcrumb.getNode()));
        NodeLocation locationInParent = firstBreadcrumb.getLocation();
        int ix = locationInParent.getIndex();
        String name = locationInParent.getName();
        List<T> childList = new ArrayList<>(childrenForParent.get(name));
        switch (modificationType) {
            case REPLACE:
                childList.set(ix, curNode);
                break;
            case DELETE:
                childList.remove(ix);
                break;
            case INSERT_BEFORE:
                childList.add(ix, curNode);
                break;
            case INSERT_AFTER:
                childList.add(ix + 1, curNode);
                break;
        }
        childrenForParent.put(name, childList);
        curNode = nodeAdapter.withNewChildren(firstBreadcrumb.getNode(), childrenForParent);
        if (breadcrumbs.size() == 1) {
            return curNode;
        }
        for (Breadcrumb<T> breadcrumb : breadcrumbs.subList(1, breadcrumbs.size())) {
            // just handle replace
            Map<String, List<T>> newChildren = new LinkedHashMap<>(nodeAdapter.getNamedChildren(breadcrumb.getNode()));
            final T newChild = curNode;
            NodeLocation location = breadcrumb.getLocation();
            List<T> list = new ArrayList<>(newChildren.get(location.getName()));
            list.set(location.getIndex(), newChild);
            newChildren.put(location.getName(), list);
            curNode = nodeAdapter.withNewChildren(breadcrumb.getNode(), newChildren);
        }
        return curNode;
    }


}
