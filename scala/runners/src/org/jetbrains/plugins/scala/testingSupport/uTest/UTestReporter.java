package org.jetbrains.plugins.scala.testingSupport.uTest;

import scala.util.Failure;
import utest.framework.Result;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

/**
 * @author Roman.Shein
 * @since 12.08.2015.
 */
public final class UTestReporter {

  private static final long NO_DURATION = -1;
  private final CountDownLatch myLatch;

  public UTestReporter(int classCount) {
    myLatch = new CountDownLatch(classCount);
  }

  private final AtomicInteger idHolder = new AtomicInteger();
  private final Map<UTestPath, Integer> testPathToId = new HashMap<>();
  private final Map<String, Integer> fqnToMethodCount = new HashMap<>();
  private final Map<UTestPath, Integer> testToClosedChildren = new HashMap<>();

  protected int getNextId() {
    return idHolder.incrementAndGet();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public synchronized boolean isStarted(UTestPath testPath) {
    return testPathToId.containsKey(testPath);
  }

  /**
   * Try to deduce location of test in class. Only works with both class name and test name are provided and test name
   * is provided in test definition as a string literal.
   *
   * @param className full qualified class name
   * @param testName  name of test under consideration
   * @return location hint in buildserver notation
   */
  private static String getLocationHint(String className, String testName) {
    String uri = escapeString("scalatest://TopOfClass:" + className + "TestName:" + escapeString(testName));
    return "locationHint='" + uri + "'";
  }

  private static String getLocationHint(String className, Method method, String testName) {
    String uri = escapeString("scalatest://TopOfMethod:" + className + ":" + method.getName() + "TestName:" + testName);
    return "locationHint='" + uri + "'";
  }

  /**
   * Reports a beginning of test suite.
   * @param classQualifiedName FQN of test suite class
   */
  public void reportClassSuiteStarted(String classQualifiedName) {
    int classScopeId = getNextId();

    UTestPath testPath = new UTestPath(classQualifiedName);
    assert(!testPathToId.containsKey(testPath));
    testPathToId.put(testPath, classScopeId);

    String suiteName = getSuiteName(classQualifiedName);
    reportStartedInner(suiteName, classScopeId, 0, getLocationHint(classQualifiedName, suiteName), true);
  }

  public void reportStarted(UTestPath testPath, boolean isScope) {
    UTestPath parent = testPath.parent();
    if (parent != null && !isStarted(parent))
      reportStarted(parent, true);

    final int parentId;
    if (parent == null) {
      //a method scope is opened, parent is class scope
      assert(isScope);
      parentId = testPathToId.get(testPath.getClassTestPath());
    } else {
      parentId = testPathToId.get(parent);
    }

    String testName = testPath.getTestName();
    int id = getNextId();
    testPathToId.put(testPath, id);
    String locationHint = getLocationHint(testPath.getQualifiedClassName(), testPath.getMethod(), testName);
    reportStartedInner(testName, id, parentId, locationHint, isScope);
  }

  private void reportStartedInner(String name, int nodeId, int parentId, String locationHint, boolean isScope) {
    String stageName = isScope ? "testSuiteStarted" : "testStarted";
    String message = String.format(
            "\n##teamcity[%s name='%s' nodeId='%d' parentNodeId='%d' %s captureStandardOutput='true']",
            stageName, escapeString(name), nodeId, parentId, locationHint
    );
    reportMessage(message);
  }

  public void reportFinished(UTestPath testPath, Result result, boolean isScope,
                             Map<UTestPath, Integer> childrenCount) {

    UTestPath parent = testPath.parent();
    if (parent != null) {
      testToClosedChildren.merge(parent, 1, Integer::sum);
    }

    if (!isScope && result != null && result.value() instanceof Failure) {
      int testId = testPathToId.get(testPath);
      String testName = testPath.getTestName();
      Failure<?> failure = (Failure<?>) result.value();
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      failure.exception().printStackTrace(printWriter);
      String message = String.format(
              "\n##teamcity[testFailed name='%s' message='%s' details='%s' nodeId='%d']",
              escapeString(testName),
              escapeString(failure.exception().getMessage()),
              escapeString(stringWriter.toString()),
              testId
      );
      reportMessage(message);
    } else {
      if (childrenCount.containsKey(testPath) && childrenCount.get(testPath) > 0) {
        if (!testToClosedChildren.containsKey(testPath)) {
          testToClosedChildren.put(testPath, 0);
        }

        if (testToClosedChildren.get(testPath).equals(childrenCount.get(testPath))) {
          //all children have been closed, report current scope
          reportScopeOrTestFinished(testPath, isScope, result);
        }
      } else {
        reportScopeOrTestFinished(testPath, isScope, result);
      }
    }
    if (parent != null) {
      reportFinished(parent, null, true, childrenCount);
    }
  }

  private void reportScopeOrTestFinished(UTestPath testPath, boolean isScope, Result result) {
    int testId = testPathToId.get(testPath);
    String testName = testPath.getTestName();
    if (testPath.isSuiteRepresentation()) {
      reportClassSuiteFinished(testPath.getQualifiedClassName());
    } else {
      reportFinishedInner(testName, testId, isScope, (!isScope && result != null) ? result.milliDuration() : NO_DURATION);
    }
  }

  /**
   * Reports the end of test suite run.
   * @param classQualifiedName FQN of test suite class
   */
  public void reportClassSuiteFinished(String classQualifiedName) {

    int classScopeId = testPathToId.get(new UTestPath(classQualifiedName));
    reportFinishedInner(getSuiteName(classQualifiedName), classScopeId, true, NO_DURATION);

    myLatch.countDown();
  }

  private void reportFinishedInner(String name, int id, boolean isScope, long duration) {
    String stageName = isScope ? "testSuiteFinished" : "testFinished";
    String durationStr = duration > 0 ? String.format("duration='%d'", duration) : "";
    String message = String.format(
            "\n##teamcity[%s name='%s' %s nodeId='%d']",
            stageName, escapeString(name), durationStr, id
    );
    reportMessage(message);
  }


  private String getSuiteName(String className) {
    int lastDotPosition = className.lastIndexOf(".");
    return (lastDotPosition != -1) ? className.substring(lastDotPosition + 1) : className;
  }

  /**
   * Increases count of 'method' test paths (i.e. test paths representing a method entry point in test suite) in current
   * test run. When all the 'method' test paths have been run, the run is considered completed, and
   */
  public void registerTestClass(String classFqn, int methodCount) {
    assert(!fqnToMethodCount.containsKey(classFqn));
    fqnToMethodCount.put(classFqn, methodCount);
  }

  /**
   * Awaits until all the tests have finished execution and have been reported to the IDE.
   */
  public void waitUntilReportingFinished() {
    try {
      myLatch.await();
    } catch (InterruptedException e) {
      reportMessage("Reporter awaiting for test execution to finish has been interrupted: " + e);
    }
  }

  public void reportMessage(String message) {
    System.out.println(message);
  }

  public void reportError(String errorMessage) {
    if (errorMessage != null) {
      System.err.println(errorMessage);
    }
    myLatch.countDown();
  }
}
