package org.jetbrains.plugins.scala.testingSupport.uTest.utils;

import org.jetbrains.plugins.scala.testingSupport.MyJavaConverters;
import org.jetbrains.plugins.scala.testingSupport.uTest.UTestPath;
import org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunExpectedError;
import utest.framework.Tree;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestErrorUtils.errorMessage;
import static org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestErrorUtils.expectedError;

public class UTestTreeUtils {

    private UTestTreeUtils() {
    }

    public static void traverseParents(UTestPath currentPath, Consumer<UTestPath> consumer) {
        UTestPath parent = currentPath.parent();
        if (parent != null) {
            consumer.accept(parent);
            traverseParents(parent, consumer);
        }
    }

    public static void traverseLeaveNodes(Tree<String> names, UTestPath currentPath, Consumer<UTestPath> leafConsumer) throws UTestRunExpectedError {
        List<Tree<String>> children = getChildren(names);
        boolean isLeaf = children.isEmpty();
        if (isLeaf) {
            leafConsumer.accept(currentPath);
        } else {
            for (Tree<String> child : children)
                traverseLeaveNodes(child, currentPath.append(child.value()), leafConsumer);
        }
    }

    public static List<Tree<String>> getChildren(Tree<String> names) throws UTestRunExpectedError {
        scala.collection.Seq<Tree<String>> children;
        try {
            children = names.children();
        } catch (NoSuchMethodError error) {
            children = getChildrenScala_2_13(names);
        }
        return MyJavaConverters.toJava(children);
    }

    @SuppressWarnings("unchecked")
    private static scala.collection.Seq<Tree<String>> getChildrenScala_2_13(Tree<String> names) throws UTestRunExpectedError {
        scala.collection.Seq<Tree<String>> children;
        try {
            Class<?> clazz = names.getClass();
            Method method = clazz.getMethod("children");
            children = (scala.collection.Seq<Tree<String>>) method.invoke(names);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw expectedError(errorMessage(e));
        }
        return children;
    }


    /**
     * testPath = x.y
     * <br>
     * root =   x (can contains extra empty root node)
     *        /   \
     *      y      a
     *     / \       \
     *    z   b       c
     *<br>
     * result: y
     *        / \
     *       z   b
     */
    public static Tree<String> getTestsSubTree(Tree<String> root, UTestPath testPath) throws UTestRunExpectedError {
        Tree<String> currentNode = root;

        for (String nodeName : testPath.getPath()) {
            Tree<String> matchingChild = findChildWithName(currentNode, nodeName);
            if (matchingChild != null)
                currentNode = matchingChild;
            else
                throw new RuntimeException("Failure in test pathing for test " + testPath);
        }

        return currentNode;
    }

    /**
     * testPath = x.y
     * <br>
     * root =   x (can contains extra empty root node)
     *        /   \
     *      y      a
     *     / \       \
     *    z   b       c
     * <br>
     * result:  x
     *        /
     *      y
     *     / \
     *    z   b
     */
    public static Tree<String> getTestsSubTreeWithPathToRoot(Tree<String> root, UTestPath testPath) throws UTestRunExpectedError {
        if (testPath.getPath().isEmpty()) return null;

        Tree<String> current = root;
        List<String> walkupNodes = new LinkedList<>();

        for (String nodeName: testPath.getPath()) {
            Tree<String> matchingChild = findChildWithName(current, nodeName);
            if (matchingChild != null) {
                if (current != root)
                    walkupNodes.add(current.value());
                current = matchingChild;
            } else {
                return null;
            }
        }

        Collections.reverse(walkupNodes);
        for (String walkup : walkupNodes) {
            List<Tree<String>> dummyChildren = Collections.singletonList(current);
            current = newTree(walkup, dummyChildren);
        }
        return current;
    }

    private static Tree<String> findChildWithName(Tree<String> node, String childNodeName) throws UTestRunExpectedError {
        return UTestTreeUtils.getChildren(node).stream()
                .filter(c -> c.value().equals(childNodeName))
                .findAny().orElse(null);
    }

    private static Tree<String> newTree(String walkup, List<Tree<String>> children) throws UTestRunExpectedError {
        scala.collection.immutable.List<Tree<String>> childrenScala = MyJavaConverters.toScala(children);
        Tree<String> tree;
        try {
            tree = new Tree<>(walkup, childrenScala);
        } catch (NoSuchMethodError error) {
            try {
                tree = newTree_213(walkup, childrenScala);
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException ignored) {
                try {
                    tree = newTree_212(walkup, childrenScala.toSeq());
                } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                    e.printStackTrace();
                    throw expectedError(errorMessage(e));
                }
            }
        }
        return tree;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> Tree<T> newTree_213(T walkup, scala.collection.immutable.List<Tree<String>> childrenScala) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        Class<Tree> clazz = Tree.class;
        Constructor<Tree> constructor = clazz.getConstructor(java.lang.Object.class, scala.collection.immutable.Seq.class);
        return (Tree<T>) constructor.newInstance(walkup, childrenScala);
    }

    @SuppressWarnings({"rawtypes", "JavaReflectionMemberAccess", "unchecked"})
    private static <T> Tree<T> newTree_212(T walkup, scala.collection.Seq<Tree<String>> childrenScala) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<Tree> clazz = Tree.class;
        Constructor<Tree> constructor = clazz.getConstructor(java.lang.Object.class, scala.collection.Seq.class);
        return (Tree<T>) constructor.newInstance(walkup, childrenScala);
    }
}
