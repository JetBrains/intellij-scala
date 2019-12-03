package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.MyJavaConverters;
import scala.Function2;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;
import utest.TestRunner;
import utest.TestRunner$;
import utest.Tests;
import utest.framework.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class UTestSuite540Runner extends UTestSuiteRunner {

  @Override
  public void doRunTestSuites(String className, Collection<UTestPath> tests, UTestReporter reporter) throws UTestRunExpectedError {
    Collection<UTestPath> testsToRun;
    Class clazz;
    Object testObject;
    try {
      clazz = Class.forName(className);
      Class testObjClass = Class.forName(className + "$");
      testObject = testObjClass.getField("MODULE$").get(null);
    } catch (ClassNotFoundException e) {
      throw expectedError(e.getClass().getSimpleName() + " for " + className + ": " + e.getMessage());
    } catch (IllegalAccessException e) {
      throw expectedError(e.getClass().getSimpleName() + " for instance field of " + className + ": " + e.getMessage());
    } catch (NoSuchFieldException e) {
      throw expectedError(e.getClass().getSimpleName() + " for instance field of  " + className + ": " + e.getMessage());
    }

    if (!tests.isEmpty()) {
      testsToRun = tests;
    } else {
      testsToRun = new LinkedList<UTestPath>();
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(Tests.class) && method.getParameterTypes().length == 0) {
          testsToRun.add(new UTestPath(className, method));
        }
      }
    }
    reporter.reportClassSuiteStarted(className);

    Set<Method> testMethods = new HashSet<Method>();
    List<UTestPath> leafTests = new LinkedList<UTestPath>();
    Map<UTestPath, Integer> childrenCount = new HashMap<UTestPath, Integer>();
    Map<UTestPath, Tests> pathToResolvedTests = new HashMap<UTestPath, Tests>();

    //collect test data required to perform test launch without scope duplication
    for (UTestPath testPath : testsToRun) {
      testMethods.add(testPath.getMethod());
      Method test = testPath.getMethod();
      Tests testTree;
      try {
        assert(Modifier.isStatic(test.getModifiers()));
        testTree = (Tests) test.invoke(null);
      } catch (IllegalAccessException e) {
        throw expectedError(e.getClass().getSimpleName() + " on test initialization for " + clazz.getName() + ": " + e.getMessage());
      } catch (InvocationTargetException e) {
        e.printStackTrace();
        throw expectedError(e.getClass().getSimpleName() + " on test initialization for " + clazz.getName() + ": " + e.getMessage());
      }


      //first, descend to the current path
      Tree<String> current = testTree.nameTree();
      for (String name : testPath.getPath()) {
        boolean changed = false;
        for (scala.collection.Iterator<Tree<String>> it =getChildren(current).iterator(); it.hasNext();) {
          Tree<String> child = it.next();
          if (child.value().equals(name)) {
            current = child;
            changed = true;
            break;
          }
        }
        if (!changed) {
          throw new RuntimeException("Failure in test pathing for test " + testPath);
        }
      }
      traverseTestTreeDown(current, testPath, leafTests);
      pathToResolvedTests.put(testPath, testTree);
    }
    countTests(childrenCount, leafTests);

    reporter.registerTestClass(className, testMethods.size());

    for (UTestPath testPath : testsToRun) {

      Tests resolveResult = pathToResolvedTests.get(testPath);
      for (UTestPath leafTest : leafTests) {
        //open all leaf tests and their outer scopes
        if (!reporter.isStarted(leafTest)) {
          reporter.reportStarted(leafTest, false);
        }
      }

      scala.Function2<scala.collection.Seq<String>, Result, BoxedUnit> reportFunction =
          getReportFunction(reporter, testPath.getMethodPath(), leafTests, childrenCount);

      Tree<String> subtree = findSubtree(resolveResult.nameTree(), testPath);
      List<Tree<String>> treeList = new LinkedList<Tree<String>>();
      if (subtree != null) {
        treeList.add(subtree);
      }

      runAsync(testObject, resolveResult, reportFunction, treeList);
    }
  }

  private void runAsync(Object testObject, Tests resolveResult, Function2<Seq<String>, Result, BoxedUnit> reportFunction, List<Tree<String>> treeList) throws UTestRunExpectedError {
    try {
      TestRunner.runAsync(
          resolveResult,
          reportFunction,
          MyJavaConverters.toScala(treeList),
          (Executor) testObject,
          ExecutionContext.RunNow$.MODULE$
      );
    } catch (NoSuchMethodError error) {
      runAsync_13(testObject, resolveResult, reportFunction, treeList);
    }
  }

  @SuppressWarnings({"JavaReflectionMemberAccess", "JavaReflectionInvocation"})
  private void runAsync_13(Object testObject, Tests resolveResult, Function2<scala.collection.Seq<String>, Result, BoxedUnit> reportFunction, List<Tree<String>> treeList) throws UTestRunExpectedError {
    try {
      Class<? extends TestRunner$> runnerClazz = TestRunner$.MODULE$.getClass();
      Class<?>[] paramTypes = {
          Tests.class,
          Function2.class,
          scala.collection.immutable.Seq.class,
          Executor.class,
          scala.concurrent.ExecutionContext.class
      };
      Object[] paramValues = {
        resolveResult,
          reportFunction,
          MyJavaConverters.toScala(treeList),
          testObject,
          ExecutionContext.RunNow$.MODULE$
      };
      Method method = runnerClazz.getDeclaredMethod("runAsync", paramTypes);
      method.invoke(TestRunner$.MODULE$, paramValues);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
      throw expectedError(errorMessage(e));
    }
  }

  private Tree<String> findSubtree(Tree<String> root, UTestPath path) throws UTestRunExpectedError {
    Tree<String> current = root;
    List<String> walkupNodes = new LinkedList<String>();
    if (path.getPath().isEmpty()) return null;
    for (String node: path.getPath()) {
      scala.collection.Seq<Tree<String>> children = getChildren(current);
      boolean changed = false;
      for (scala.collection.Iterator<Tree<String>> it = children.iterator(); it.hasNext();) {
        Tree<String> check = it.next();
        if (check.value().equals(node)) {
          if (current != root) {
            walkupNodes.add(current.value());
          }
          current = check;
          changed = true;
          break;
        }
      }
      if (!changed) return null;
    }
    Collections.reverse(walkupNodes);
    for (String walkup : walkupNodes) {
      List<Tree<String>> dummyList = new LinkedList<Tree<String>>();
      dummyList.add(current);
      current = newTree(walkup, dummyList);
    }
    return current;
  }

  private Tree<String> newTree(String walkup, List<Tree<String>> children) throws UTestRunExpectedError {
    scala.collection.immutable.List<Tree<String>> childrenScala = MyJavaConverters.toScala(children);
    Tree<String> tree;
    try {
      tree = new Tree<>(walkup, childrenScala);
    } catch (NoSuchMethodError error) {
      tree = newTree_213(walkup, childrenScala);
    }
    return tree;
  }

  @SuppressWarnings({"rawtypes", "JavaReflectionMemberAccess", "unchecked"})
  private <T> Tree<T> newTree_213(T walkup, scala.collection.immutable.List<Tree<String>> childrenScala) throws UTestRunExpectedError {
    try {
      Class<Tree> clazz = Tree.class;
      Constructor<Tree> constructor = clazz.getConstructor(java.lang.Object.class, scala.collection.immutable.Seq.class);
      return (Tree<T>) constructor.newInstance(walkup, childrenScala);
    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      e.printStackTrace();
      throw expectedError(errorMessage(e));
    }
  }

  private void traverseTestTreeDown(Tree<String> names, UTestPath currentPath, List<UTestPath> leafTests) throws UTestRunExpectedError {
    scala.collection.Seq<Tree<String>> children = getChildren(names);

    if (children.isEmpty()) {
      leafTests.add(currentPath);
    } else {
      for (int i = 0; i < children.size(); i++) {
        Tree<String> child = children.apply(i);
        traverseTestTreeDown(child, currentPath.append(child.value()), leafTests);
      }
    }
  }

  private scala.collection.Seq<Tree<String>> getChildren(Tree<String> names) throws UTestRunExpectedError {
    scala.collection.Seq<Tree<String>> children;
    try {
      children = names.children();
    } catch (NoSuchMethodError error) {
      children = getChildren_2_13(names);
    }
    return children;
  }

  @SuppressWarnings("unchecked")
  private scala.collection.Seq<Tree<String>> getChildren_2_13(Tree<String> names) throws UTestRunExpectedError {
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

  private String errorMessage(ReflectiveOperationException e) {
    return e.getClass().getSimpleName() + ": " + e.getMessage();
  }
}
