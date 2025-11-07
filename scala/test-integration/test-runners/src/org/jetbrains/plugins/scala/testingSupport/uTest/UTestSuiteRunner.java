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

  /**
   * @param testClassFqn class name of the "class", NOT the companion "object" (this doesn't contain $ unless it's explicitly defined in the class name).<br>
   *                 Regardless of whether the test is defined as "class" or as "object" this name always represents the class.
   */
  private void doRunTestSuite(String testClassFqn, Collection<UTestPath> tests) throws UTestRunExpectedError {
    final Object testObject = loadTestModule(testClassFqn);

    final Class<?> testObjectClass = testObject.getClass();
    final Collection<UTestPath> testsToRun = !tests.isEmpty()
        ? tests
        : Collections.singletonList(UTestUtils.findTestsNode(testObjectClass, testClassFqn));

    final Method testsMethod = testsToRun.iterator().next().getMethod();
    final UTestPath testsMethodPath = UTestPath.getMethodPath(testClassFqn, testsMethod);

    final Tests testHolder = getTestsTreeHolder(testObject, testsMethod, testClassFqn);

    List<UTestPath> leafTests = collectLeafTestsToRun(testsToRun, testHolder.nameTree());
    Map<UTestPath, Integer> childrenCount = getChildrenCountMap(leafTests);

    //open all leaf tests and their outer scopes
    // TODO: do not open all leaves at once cause it visually looks like we run all tests in parallel, which is wrong
    //  It could be achieved using special TC service messages like ##teamcity[suiteTreeStarted, ##teamcity[suiteTreeEnded or something like that...
    //  (see example in com.intellij.junit4.JUnit4TestListener.sendTree(org.junit.runner.Description)
    //  However UTest currently lacks API to report "test started" event. It only supports
    //  "onComplete" callback in utest.TestRunner.runAsync
    for (UTestPath leafTest : leafTests)
      // Report that tests are started => IntelliJ will create test nodes in the test tree view
      if (!reporter.isStarted(leafTest))
        reporter.reportTestStarted(leafTest);

    for (UTestPath testPath : testsToRun) {
      Tree<String> subtree = UTestTreeUtils.getTestsSubTreeWithPathToRoot(testHolder.nameTree(), testPath);
      List<Tree<String>> treeList = subtree != null
              ? Collections.singletonList(subtree)
              : Collections.emptyList();

      runAsync(testObject, testHolder, treeList, ((result, finishedTestPath) -> {
        UTestPath absoluteFinishedTestPath = testsMethodPath.append(finishedTestPath);
        boolean isLeafTest = leafTests.contains(absoluteFinishedTestPath);
        if (isLeafTest) {
          boolean isClassSuiteFinished = reporter.reportTestFinished(absoluteFinishedTestPath, result, childrenCount);
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

  private Tests getTestsTreeHolder(Object testObject, Method testMethod, String debugTestClassName) throws UTestRunExpectedError {
    try {
      return (Tests) testMethod.invoke(testObject);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw expectedError(e.getClass().getSimpleName() + " on test initialization for " + debugTestClassName + ": " + e.getMessage());
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

  /**
   * @return An instance from `MODULE$` if the test was defined as "object" (in pre-0.9 style).<br>
   * Or a newly created instance of a class if the test was defined as a "class" (in 0.9 style)
   */
  private Object loadTestModule(String className) throws UTestRunExpectedError {
    try {
      return loadTestModuleTryingDifferentVersions(className);
    } catch (ClassNotFoundException e) {
      throw expectedError(e.getClass().getSimpleName() + " for " + className + ": " + e.getMessage());
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw expectedError(e.getClass().getSimpleName() + " for instance field of " + className + ": " + e.getMessage());
    }
  }

  private Object loadTestModuleTryingDifferentVersions(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    try {
      return loadTestModule_Since_uTest_0_9(className);
    } catch (ReflectiveOperationException e) {
      return loadTestModule_Before_uTest_0_9(className);
    }
  }

  private static final String PlatformShimsFqn_Before_0_9 = "utest.PlatformShims";
  private static final String PlatformShimsFqn_Since_0_9 = "utest.framework.PlatformShims";

  private Object loadTestModule_Since_uTest_0_9(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException  {
    ClassLoader selfClassLoader = this.getClass().getClassLoader();
    Class<?> platformShimsClass = selfClassLoader.loadClass(PlatformShimsFqn_Since_0_9);
    Method loadModuleMethod = platformShimsClass.getMethod("loadModule", String.class, ClassLoader.class);
    return loadModuleMethod.invoke(null, className, selfClassLoader);
  }

  // utest.framework.PlatformShims in 0.9 was utest.PlatformShims in 0.8.x
  private Object loadTestModule_Before_uTest_0_9(String className) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    ClassLoader selfClassLoader = this.getClass().getClassLoader();
    Class<?> platformShimsClass = selfClassLoader.loadClass(PlatformShimsFqn_Before_0_9);
    Method loadModuleMethod = platformShimsClass.getMethod("loadModule", String.class, ClassLoader.class);
    return loadModuleMethod.invoke(null, className, selfClassLoader);
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
