package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.testingSupport.MyJavaConverters;
import org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestTreeUtils;
import org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestUtils;
import scala.Function2;
import scala.collection.immutable.Seq;
import scala.runtime.BoxedUnit;
import utest.TestRunner;
import utest.TestRunner$;
import utest.Tests;
import utest.framework.ExecutionContext;
import utest.framework.Executor;
import utest.framework.Result;
import utest.framework.Tree;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestErrorUtils.errorMessage;
import static org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestErrorUtils.expectedError;

/**
 * Current supported version: 0.7.x<br>
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
      runTestSuite(className, suitesAndTests.get(className));
    }

    try {
      testSuitesLatch.await();
    } catch (InterruptedException e) {
      reporter.reportError("Reporter awaiting for test execution to finish has been interrupted: " + e);
    }
  }

  private void runTestSuite(String suiteClassName, Collection<UTestPath> tests) {
    try {
      doRunTestSuite(suiteClassName, tests);
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

  private void doRunTestSuite(String classFqn, Collection<UTestPath> tests) throws UTestRunExpectedError {
    final Class<?> clazz = getTestClass(classFqn);
    final Object testObject = getTestObject(classFqn);

    final Collection<UTestPath> testsToRun = !tests.isEmpty()
            ? tests
            : Collections.singletonList(UTestUtils.findTestsNode(clazz));

    final Method testsMethod = testsToRun.iterator().next().getMethod();
    final UTestPath testsMethodPath = UTestPath.getMethodPath(classFqn, testsMethod);
    final Tests testHolder = getTestsTreeHolder(clazz, testsMethod);

    List<UTestPath> leafTests = collectLeafTestsToRun(testsToRun, testHolder.nameTree());
    Map<UTestPath, Integer> childrenCount = getChildrenCountMap(leafTests);

    //open all leaf tests and their outer scopes
    // TODO: do not open all leaves at once cause it visually looks like we run all tests in parallel, which is wrong
    for (UTestPath leafTest : leafTests)
      if (!reporter.isStarted(leafTest))
        reporter.reportTestStarted(leafTest);

    for (UTestPath testPath : testsToRun) {
      Tree<String> subtree = UTestTreeUtils.getTestsSubTreeWithPathToRoot(testHolder.nameTree(), testPath);
      List<Tree<String>> treeList = subtree != null
              ? Collections.singletonList(subtree)
              : Collections.emptyList();

      runAsync(testObject, testHolder, treeList, ((result, finishedTestPath) -> {
        UTestPath absolutePath = testsMethodPath.append(finishedTestPath);
        boolean isLeafTest = leafTests.contains(absolutePath);
        if (isLeafTest) {
          boolean isClassSuiteFinished = reporter.reportTestFinished(absolutePath, result, childrenCount);
          if (isClassSuiteFinished)
            testSuiteFinished();
        }
      }));
    }
  }

  private List<UTestPath> collectLeafTestsToRun(Collection<UTestPath> testsToRun, Tree<String> root) throws UTestRunExpectedError {
    LinkedList<UTestPath> leaves = new LinkedList<>();
    for (UTestPath testPath : testsToRun) {
      Tree<String> current = UTestTreeUtils.getTestsSubTree(root, testPath);
      UTestTreeUtils.traverseLeaveNodes(current, testPath, leaves::add);
    }
    return leaves;
  }

  private Tests getTestsTreeHolder(Class<?> clazz, Method testMethod) throws UTestRunExpectedError {
    try {
      return (Tests) testMethod.invoke(null);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw expectedError(e.getClass().getSimpleName() + " on test initialization for " + clazz.getName() + ": " + e.getMessage());
    }
  }

  private Map<UTestPath, Integer> getChildrenCountMap(List<UTestPath> leafTests) {
    Map<UTestPath, Integer> result = new LinkedHashMap<>();
    for (UTestPath leaf: leafTests)
      UTestTreeUtils.traverseParents(leaf, parent -> result.merge(parent, 1, Integer::sum));
    return result;
  }

  @NotNull
  private Class<?> getTestClass(String className) throws UTestRunExpectedError {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw expectedError(e.getClass().getSimpleName() + " for " + className + ": " + e.getMessage());
    }
  }

  private Object getTestObject(String className) throws UTestRunExpectedError {
    try {
      Class<?> testObjClass = Class.forName(className + "$");
      return testObjClass.getField("MODULE$").get(null);
    } catch (ClassNotFoundException e) {
      throw expectedError(e.getClass().getSimpleName() + " for " + className + ": " + e.getMessage());
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw expectedError(e.getClass().getSimpleName() + " for instance field of " + className + ": " + e.getMessage());
    }
  }

  private void runAsync(
          final Object testObject,
          final Tests testsHolder,
          final List<Tree<String>> treeList,
          final TestFinishListener listener
  ) throws UTestRunExpectedError {
    runAsync(testObject, testsHolder, treeList, new ReportFunction(listener));
  }

  private void runAsync(
          final Object testObject,
          final Tests testsHolder,
          final List<Tree<String>> treeList,
          final Function2<Seq<String>, Result, BoxedUnit> reportFunction
  ) throws UTestRunExpectedError {
    try {
      //noinspection unchecked
      TestRunner.runAsync(
              testsHolder,
              reportFunction,
              MyJavaConverters.<Tree<String>>toScala(treeList),
              (Executor) testObject,
              ExecutionContext.RunNow$.MODULE$
      );
    } catch (NoSuchMethodError error) {
      runAsync_Scala_2_13(testObject, testsHolder, treeList, reportFunction);
    }
  }

  @SuppressWarnings({"JavaReflectionMemberAccess", "JavaReflectionInvocation"})
  private void runAsync_Scala_2_13(
          final Object testObject,
          final Tests testsHolder,
          final List<Tree<String>> treeList,
          final Function2<Seq<String>, Result, BoxedUnit> reportFunction
  ) throws UTestRunExpectedError {
    try {
      Class<? extends TestRunner$> runnerClazz = TestRunner$.MODULE$.getClass();
      Class<?>[] paramTypes = {
              Tests.class,
              Function2.class,
              scala.collection.Seq.class,
              Executor.class,
              scala.concurrent.ExecutionContext.class
      };
      Object[] paramValues = {
              testsHolder,
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

  private static class ReportFunction extends scala.runtime.AbstractFunction2<Seq<String>, Result, BoxedUnit> {
    final TestFinishListener listener;

    private ReportFunction(TestFinishListener listener) {
      this.listener = listener;
    }

    @Override
    public BoxedUnit apply(Seq<String> seq, Result result) {
      synchronized (listener) {
        List<String> resSeq = MyJavaConverters.toJava(seq);
        listener.testFinished(result, resSeq);
        return BoxedUnit.UNIT;
      }
    }
  }

  @FunctionalInterface
  private interface TestFinishListener {
    void testFinished(Result result, List<String> resSeq);
  }
}
