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
public class UTestReporter {

  private static final long NO_DURATION = -1;
  private final CountDownLatch myLatch;

  public UTestReporter(int classCount) {
    myLatch = new CountDownLatch(classCount);
  }

  private AtomicInteger idHolder = new AtomicInteger();
  private final Map<UTestPath, Integer> testPathToId = new HashMap<UTestPath, Integer>();
  private final Map<String, Integer> fqnToMethodCount = new HashMap<String, Integer>();
  private final Map<UTestPath, Integer> testToClosedChildren = new HashMap<UTestPath, Integer>();

  protected int getNextId() {
    return idHolder.incrementAndGet();
  }

  public synchronized boolean isStarted(UTestPath testPath) {
    return testPathToId.containsKey(testPath);
  }

  /**
   * Try to deduce location of test in class. Only works whtn both class name and test name are provided and test name
   * is provided in test definition as a string literal.
   *
   * @param className full qualified class name
   * @param testName  name of test under consideration
   * @return location hint in buildserver notation
   */
  private static String getLocationHint(String className, String testName) {
    return " locationHint='" + escapeString("scalatest://TopOfClass:" + className + "TestName:" + escapeString(testName)) + "'";
  }

  private static String getLocationHint(String className, Method method, String testName) {
    return " locationHint='" + escapeString("scalatest://TopOfMethod:" + className + ":" + method.getName() + "TestName:" + testName) + "'";
  }


  public void reportStarted(UTestPath testPath, boolean isScope) {
    UTestPath parent = testPath.parent();
    String testName = testPath.getTestName();
    if (parent == null) {
      //a method scope is opened, parent is class scope
      int parentId = testPathToId.get(testPath.getclassTestPath());
      int id = getNextId();
      assert(isScope);
      testPathToId.put(testPath, id);
      reportStartedInner(testName, id, parentId, getLocationHint(testPath.getQualifiedClassName(), testPath.getMethod(), testName), isScope);
    } else {
      if (!isStarted(parent)) {
        reportStarted(parent, true);
      }
      int parentId = testPathToId.get(parent);
      int id = getNextId();
      testPathToId.put(testPath, id);
      reportStartedInner(testName, id, parentId, getLocationHint(testPath.getQualifiedClassName(), testPath.getMethod(), testName), isScope);
    }
  }

  public void reportFinished(UTestPath testPath, Result result, boolean isScope,
                             Map<UTestPath, Integer> childrenCount) {

    UTestPath parent = testPath.parent();
    if (parent != null) {
      if (testToClosedChildren.get(parent) == null) {
        testToClosedChildren.put(parent, 1);
      } else {
        testToClosedChildren.put(parent, testToClosedChildren.get(parent) + 1);
      }
    }

    if (!isScope && result != null && result.value() instanceof Failure) {
      int testId = testPathToId.get(testPath);
      String testName = testPath.getTestName();
      Failure failure = (Failure) result.value();
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      failure.exception().printStackTrace(printWriter);
      System.out.println("\n##teamcity[testFailed name='" + escapeString(testName) + "' message='" +
          escapeString(failure.exception().getMessage()) +
          "' details='" + escapeString(stringWriter.toString()) + "' nodeId='" + testId + "']");
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

  private void reportStartedInner(String name, int nodeId, int parentId, String locationHint, boolean isScope) {
    System.out.println("\n##teamcity[" + (isScope ? "testSuiteStarted" : "testStarted") + " name='" +
        escapeString(name) + "' nodeId='" + nodeId + "' parentNodeId='" + parentId + "'" + locationHint +
        " captureStandardOutput='true']");
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

  private void reportFinishedInner(String name, int id, boolean isScope, long duration) {
    System.out.println("\n##teamcity[" + (isScope ? "testSuiteFinished" : "testFinished") + " name='" +
        escapeString(name) + (duration > 0 ? "' duration='" + duration : "") + "' nodeId='" + id + "']");
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

  private String getSuiteName(String className) {
    int lastDotPosition = className.lastIndexOf(".");
    return (lastDotPosition != -1) ? className.substring(lastDotPosition + 1) : className;
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
      System.out.println("Reporter awaiting for test execution to finish has been interrupted: " + e);
    }
  }
}
