package org.jetbrains.plugins.scala.testingSupport.uTest;

import scala.util.Failure;
import utest.framework.Result;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

public final class UTestReporter {

  private static final long NO_DURATION = -1;

  private final AtomicInteger idHolder = new AtomicInteger();
  private final Map<UTestPath, Integer> testPathToId = new HashMap<>();
  private final Map<UTestPath, Integer> testToClosedChildren = new HashMap<>();

  protected int getNextId() {
    return idHolder.incrementAndGet();
  }

  private int allocateIdForPath(UTestPath testPath) {
    int id = getNextId();
    assert(!testPathToId.containsKey(testPath));
    testPathToId.put(testPath, id);
    return id;
  }


  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public synchronized boolean isStarted(UTestPath testPath) {
    return testPathToId.containsKey(testPath);
  }

  // TODO: location hings do not work for nested tests,
  //   see org.jetbrains.plugins.scala.testingSupport.locationProvider.ScalaTestLocationProvider
  private static String getClassSuiteLocationHint(UTestPath suitPath) {
    String className = suitPath.getQualifiedClassName();
    String testName = suitPath.getTestName();
    String uri = escapeString("scalatest://TopOfClass:" + className + "TestName:" + escapeString(testName));
    return "locationHint='" + uri + "'";
  }

  private static String getScopeLocationHint(UTestPath testPath) {
    return getTestLocationHint(testPath);
  }

  private static String getTestLocationHint(UTestPath testPath) {
    String className = testPath.getQualifiedClassName();
    String methodName = testPath.getMethod().getName();
    String testName = testPath.getTestName();
    String uri = escapeString("scalatest://TopOfMethod:" + className + ":" + methodName + "TestName:" + testName);
    return "locationHint='" + uri + "'";
  }

  public void reportTestStarted(UTestPath testPath) {
    UTestPath parent = testPath.parent();
    if (parent != null) // should also be true
      reportScopeStarted(parent);

    reportTestStarted(testPath, getTestLocationHint(testPath));
  }

  private void reportScopeStarted(UTestPath scopePath) {
    if (isStarted(scopePath))
      return;

    UTestPath parent = scopePath.parent();
    if (parent == null) {
      reportClassSuiteStarted(scopePath);
    } else {
      reportScopeStarted(parent);
      reportScopeStarted(scopePath, getScopeLocationHint(scopePath));
    }
  }

  public void reportClassSuiteStarted(UTestPath suitePath) {
    String locationHint = getClassSuiteLocationHint(suitePath);
    reportScopeStarted(suitePath, 0, locationHint);
  }

  private void reportScopeStarted(UTestPath testPath, int parentId, String locationHint) {
    String testName = testPath.getTestName();
    int nodeId = allocateIdForPath(testPath);
    String message = String.format(
            "##teamcity[testSuiteStarted name='%s' nodeId='%d' parentNodeId='%d' %s captureStandardOutput='true']",
            escapeString(testName), nodeId, parentId, locationHint
    );
    reportMessage(message);
  }

  private void reportScopeStarted(UTestPath testPath, String locationHint) {
    int parentId = testPathToId.get(testPath.parent());
    reportScopeStarted(testPath, parentId, locationHint);
  }

  private void reportTestStarted(UTestPath testPath, String locationHint) {
    String testName = testPath.getTestName();
    int nodeId = allocateIdForPath(testPath);
    int parentNodeId = testPathToId.get(testPath.parent());
    String message = String.format(
            "##teamcity[testStarted name='%s' nodeId='%d' parentNodeId='%d' %s captureStandardOutput='true']",
            escapeString(testName), nodeId, parentNodeId, locationHint
    );
    reportMessage(message);
  }

  /**
   * @param childrenCount number of children for scope nodes, doesn't contain leaves
   * @return true if class suite is finished, false otherwise
   */
  public boolean reportTestFinished(UTestPath testPath,
                                    Result result,
                                    Map<UTestPath, Integer> childrenCount) {
    if (isFailedResult(result))
      reportTestFinishedFailure(testPath, result);
    else
      reportTestFinishedSuccess(testPath, result != null ? result.milliDuration() : NO_DURATION);

    // parent for leave shouldn't be null, but check just in case
    UTestPath parent = testPath.parent();
    return parent != null && reportScopeFinished(parent, childrenCount);
  }

  private boolean isFailedResult(Result result) {
    return result != null && result.value() instanceof Failure;
  }

  private boolean reportScopeFinished(UTestPath scopePath, Map<UTestPath, Integer> childrenCount) {
    UTestPath parent = scopePath.parent();
    boolean isTestSuitePath = parent == null;

    int closedScopeChildren = testToClosedChildren.merge(scopePath, 1, Integer::sum);
    boolean allChildrenClosed = closedScopeChildren == childrenCount.get(scopePath);
    if (allChildrenClosed)
      if (isTestSuitePath)
        reportClassSuiteFinished(scopePath);
      else
        reportScopeFinished(scopePath);

    return isTestSuitePath || reportScopeFinished(parent, childrenCount);
  }

  private void reportClassSuiteFinished(UTestPath suitePath) {
    reportScopeFinished(suitePath);
  }

  private void reportScopeFinished(UTestPath testPath) {
    String name = testPath.getTestName();
    int nodeId = testPathToId.get(testPath);
    String message = String.format(
            "##teamcity[%s name='%s' nodeId='%d']",
            "testSuiteFinished", escapeString(name), nodeId
    );
    reportMessage(message);
  }

  private void reportTestFinishedSuccess(UTestPath testPath, long duration) {
    String testName = testPath.getTestName();
    int testId = testPathToId.get(testPath);
    String durationStr = duration > 0 ? String.format("duration='%d'", duration) : "";
    String message = String.format(
            "##teamcity[%s name='%s' %s nodeId='%d']",
            "testFinished", escapeString(testName), durationStr, testId
    );
    reportMessage(message);
  }

  private void reportTestFinishedFailure(UTestPath testPath, Result result) {
    int testId = testPathToId.get(testPath);
    String testName = testPath.getTestName();
    Throwable exception = ((Failure<?>) result.value()).exception();
    String message = String.format(
            "##teamcity[testFailed name='%s' message='%s' details='%s' nodeId='%d']",
            escapeString(testName),
            escapeString(exception.getMessage()),
            escapeString(getStacktraceText(exception)),
            testId
    );
    reportMessage(message);
  }

  private String getStacktraceText(Throwable exception) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    exception.printStackTrace(printWriter);
    return stringWriter.toString();
  }

  public void reportMessage(String message) {
    //new line prefix needed cause there can be some user unflushed output
    System.out.println("\n" + message);
  }

  public void reportError(String errorMessage) {
    if (errorMessage != null) {
      System.err.println(errorMessage);
    }
  }
}
