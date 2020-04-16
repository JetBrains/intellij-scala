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
import java.util.concurrent.CountDownLatch;

/**
 * Current supported version: 0.7.x (0.5.x should also work, but not tested)
 * Class is not reusable due to reused CountDownLatch
 */
public final class UTestSuiteRunner  {

  protected final UTestReporter reporter;

  protected CountDownLatch testSuitesLatch;

  public UTestSuiteRunner(UTestReporter reporter) {
    this.reporter = reporter;
  }

  final public void runTestSuites(Map<String, Set<UTestPath>> suitesAndTests) {
    int suitesCount = suitesAndTests.size();
    testSuitesLatch = new CountDownLatch(suitesCount);

    for (String className : suitesAndTests.keySet()) {
      runTestSuites(className, suitesAndTests.get(className));
    }

    try {
      testSuitesLatch.await();
    } catch (InterruptedException e) {
      reporter.reportError("Reporter awaiting for test execution to finish has been interrupted: " + e);
    }
  }

  private void runTestSuites(String className, Collection<UTestPath> tests) {
    try {
      doRunTestSuites(className, tests);
    } catch (UTestRunExpectedError expectedError) {
      reporter.reportError(expectedError.getMessage());
      testSuiteFinished();
    } catch (Throwable ex) {
      reporter.reportError(ex.getMessage());
      testSuiteFinished();
      ex.printStackTrace();
      throw ex;
    }
  }

  private void testSuiteFinished() {
    testSuitesLatch.countDown();
  }

  private UTestRunExpectedError expectedError(String message) {
    return new UTestRunExpectedError(message);
  }

  private void doRunTestSuites(String className, Collection<UTestPath> tests) throws UTestRunExpectedError {
    Collection<UTestPath> testsToRun;
    Class<?> clazz;
    Object testObject;
    try {
      clazz = Class.forName(className);
      Class<?> testObjClass = Class.forName(className + "$");
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
      testsToRun = new LinkedList<>();
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(Tests.class) && method.getParameterTypes().length == 0) {
          testsToRun.add(new UTestPath(className, method));
        }
      }
    }
    reporter.reportClassSuiteStarted(className);

    Set<Method> testMethods = new HashSet<>();
    List<UTestPath> leafTests = new LinkedList<>();
    Map<UTestPath, Integer> childrenCount = new HashMap<>();
    Map<UTestPath, Tests> pathToResolvedTests = new HashMap<>();

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

      Tree<String> subtree = findSubtree(resolveResult.nameTree(), testPath);
      List<Tree<String>> treeList = new LinkedList<>();
      if (subtree != null) {
        treeList.add(subtree);
      }

      TestFinishListener listener = (result, resSeq) -> {
        UTestPath resTestPath = testPath.getMethodPath().append(resSeq);
        if (leafTests.contains(resTestPath)) {
          boolean isScope = !leafTests.contains(resTestPath);
          boolean isClassSuiteFinished = reporter.reportFinished(resTestPath, result, isScope, childrenCount);
          if (isClassSuiteFinished)
            testSuiteFinished();
        }
      };
      runAsync(testObject, resolveResult, treeList, new ReportFunction(listener));
    }
  }

  protected void countTests(Map<UTestPath, Integer> childrenCount, List<UTestPath> leafTests) {
    for (UTestPath leaf: leafTests) {
      traverseTestTreeUp(leaf, childrenCount);
    }
  }

  private void traverseTestTreeUp(UTestPath currentPath, Map<UTestPath, Integer> childrenCount) {
    UTestPath parent = currentPath.parent();
    if (parent != null) {
      if (childrenCount.containsKey(parent)) {
        childrenCount.put(parent, childrenCount.get(parent) + 1);
      } else {
        childrenCount.put(parent, 1);
      }
      traverseTestTreeUp(parent, childrenCount);
    }
  }

  private void runAsync(
          final Object testObject,
          final Tests resolveResult,
          final List<Tree<String>> treeList,
          final Function2<Seq<String>, Result, BoxedUnit> reportFunction
  ) throws UTestRunExpectedError {
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
    List<String> walkupNodes = new LinkedList<>();
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
      List<Tree<String>> dummyList = new LinkedList<>();
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

  private static class ReportFunction extends scala.runtime.AbstractFunction2<Seq<String>, Result, BoxedUnit> {
    final TestFinishListener listener;

    private ReportFunction(TestFinishListener listener) {
      this.listener = listener;
    }

    @Override
    public BoxedUnit apply(Seq<String> seq, Result result) {
      synchronized (listener) {
        //this is a temporary implementation
        List<String> resSeq = MyJavaConverters.toJava(seq);
        listener.testFinished(result, resSeq);
        return BoxedUnit.UNIT;
      }
    }
  }

  @FunctionalInterface
  private interface TestFinishListener {

    // TODO: more meaningful naming
    void testFinished(Result result, List<String> resSeq);
  }
}
