package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import scala.concurrent.ExecutionContext;
import scala.util.Failure;
import utest.framework.Result;
import utest.framework.Test;
import utest.framework.TestTreeSeq;
import utest.util.Tree;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;

public class UTestRunner {

  /**
   * Try to deduce location of test in class. Only works whtn both class name and test name are provided and test name
   * is provided in test definition as a string literal.
   *
   * @param className full qualified class name
   * @param testName  name of test under consideration
   * @return location hint in buildserver notation
   */
  private static String getLocationHint(String className, String testName) {
    return " locationHint='scalatest://TopOfClass:" + className + "TestName:" + escapeString(testName) + "'";
  }

  private static String getLocationHint(String className, Method method, String testName) {
    return " locationHint='scalatest://TopOfMethod:" + className + ":" + method.getName() + "TestName:" + testName + "'";
  }

  private static int traverseResults(Tree<Result> result, String suiteClassName, boolean outerSuite, int parentId, int prevId, Method method) {
    Result currentRes = result.value();
    String testName = currentRes.name();//namePrefix + (outerSuite ? "" : "\\" + currentRes.name());
    String suiteName = currentRes.name();
    int currentTestId = prevId;
    int nextPrevId = currentTestId;
    int suiteId = currentTestId;
    if (!outerSuite) {
      currentTestId++;
      nextPrevId++;
      suiteId++;
      String locationHint = getLocationHint(suiteClassName, method, testName);
      if (result.children().nonEmpty()) {
        suiteId++;
        nextPrevId++;
        //it's a test with inner tests, build tree structure
        System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "' nodeId='" + suiteId +
            "' parentNodeId='" + parentId + "'" + locationHint + " captureStandardOutput='true']");
      }
      System.out.println("\n##teamcity[testStarted name='" + escapeString(testName) +
          "' nodeId='" + currentTestId + "' parentNodeId='" + parentId + "'" + locationHint + " captureStandardOutput='true']");
    }
    for (scala.collection.Iterator<Tree<Result>> it =  result.children().iterator(); it.hasNext();) {
      nextPrevId = traverseResults(it.next(), suiteClassName, false, suiteId, nextPrevId, method);
    }
    if (!outerSuite) {
      if (currentRes.value() instanceof Failure) {
        Failure failure = (Failure) currentRes.value();
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        failure.exception().printStackTrace(printWriter);
        System.out.println("\n##teamcity[testFailed name='" + escapeString(testName) + "' message='" + escapeString(failure.exception().getMessage()) +
            "' details='" + escapeString(stringWriter.toString()) + "' nodeId='" + currentTestId + "']");
      } else {
        System.out.println("\n##teamcity[testFinished name='" + escapeString(testName) +
            "' duration='" + currentRes.milliDuration() + "' nodeId='" + currentTestId + "']");
      }
      if (result.children().nonEmpty()) {
        //it's a test with inner tests, build tree structure
        System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(suiteName) + "' nodeId='"+ suiteId + "']");
      }
    }
    return nextPrevId;
  }

  private static class TestPath {
    private String testName;
    private List<String> path;

    public TestPath(String testName, List<String> path) {
      this.testName = testName;
      this.path = path;
    }

    public void cutOff(int length) {
      path = path.subList(0, length - 1);
    }

    @Override
    public String toString() {
      StringBuilder resBuilder = new StringBuilder(testName).append("|");
      for (String pathMember: path) {
        resBuilder.append("\\").append(pathMember);
      }
      return resBuilder.toString();
    }
  }

  private static class TestMethod {
    private TestPath testPath;
    private Method method;

    public TestMethod(Method method, TestPath testPath) {
      this.testPath = testPath;
      this.method = method;
    }

    public TestMethod(Method method) {
      this.method = method;
    }
  }

  private static TestPath parseTestPath(String argsString) {
    String[] nameArgs = argsString.split("\\\\");
    String testName = nameArgs[0];
    List<String> asList = Arrays.asList(nameArgs);
    List<String> testPath = asList.subList(1, asList.size());
    return new TestPath(testName, testPath);
  }

  private static int getCommonPrefixLength(TestPath first, TestPath second) {
    Iterator<String> firstPathIt = first.path.iterator();
    Iterator<String> secondPathIt = second.path.iterator();
    int counter = first.testName.equals(second.testName) ? 1 : 0;
    while(firstPathIt.hasNext() && secondPathIt.hasNext()) {
      if (!firstPathIt.next().equals(secondPathIt.next())) {
        break;
      }
      counter++;
    }
    return counter;
  }

  private static void runTestSuites(String className, Collection<TestPath> tests) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Class clazz = Class.forName(className);
    int lastDotPosition = className.lastIndexOf(".");
    String suiteName = (lastDotPosition != -1) ? className.substring(lastDotPosition + 1) : className;
    List<TestMethod> testsToRun = new LinkedList<TestMethod>();
    if (!tests.isEmpty()) {
      ArrayList<TestPath> runTests = new ArrayList<TestPath>();

      for (TestPath testPath: tests) {
        int[] prefixLength = new int[runTests.size()];
        int maxPrefixLength = 0;
        int maxPrefixIndex = -1;
        for (int i = 0; i < runTests.size(); i++) {
          prefixLength[i] = getCommonPrefixLength(testPath, runTests.get(i));
          if (prefixLength[i] > maxPrefixLength) {
            maxPrefixLength =  prefixLength[i];
            maxPrefixIndex = i;
          }
        }
        if (maxPrefixLength == 0) {
          //it's a new test that has no common prefix with other tests
          runTests.add(testPath);
        } else {
          //generalize
          runTests.get(maxPrefixIndex).cutOff(maxPrefixLength);
        }
      }

      for (TestPath testPath: runTests) {
        Method test = clazz.getMethod(testPath.testName);
        assert (test.getReturnType().equals(Tree.class));
        testsToRun.add(new TestMethod(test, testPath));
      }
    } else {
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(Tree.class) && method.getParameterTypes().length == 0) {
          testsToRun.add(new TestMethod(method));
        }
      }
    }
    int testCount = testsToRun.size();
    System.out.println("##teamcity[testCount count='" + testCount + "']");
    int parentNodeId = 0;
    int nodeId = 1;
    int prevId = nodeId;
    System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "'" + getLocationHint(className, suiteName) +
        " nodeId='" + nodeId + "' parentNodeId='" + parentNodeId + "' captureStandardOutput='true']");
    for (TestMethod testMethod : testsToRun) {
      Method test = testMethod.method;
      Tree<Test> testTree = (Tree) test.invoke(null);

      TestTreeSeq treeSeq = new TestTreeSeq(testTree);
      String testMethodName = test.getName();
      String locationHint = getLocationHint(className, testMethodName);
      int currentSuiteId = ++prevId;
      System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(testMethodName) + "' nodeId='" +
          currentSuiteId + "' parentNodeId='" + nodeId + "'" + locationHint + " captureStandardOutput='true']");
      Tree<Result> result = treeSeq.run(treeSeq.run$default$1(),
          treeSeq.run$default$2(),
          testMethod.testPath != null ? scala.collection.JavaConversions.asScalaBuffer(testMethod.testPath.path).toList() : treeSeq.run$default$3(),
          ExecutionContext.Implicits$.MODULE$.global());
      boolean classTestKind = testMethod.testPath == null || testMethod.testPath.path.isEmpty();
      prevId = traverseResults(result, clazz.getName(), classTestKind, currentSuiteId, prevId, test);

      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(testMethodName) + "' nodeId='" + currentSuiteId + "']");
    }
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(suiteName) + "' nodeId='" + nodeId + "']");
  }


  public static void main(String[] args) throws IOException,
      ClassNotFoundException,
      NoSuchMethodException,
      NoSuchFieldException,
      InvocationTargetException,
      IllegalAccessException {
    String[] newArgs = TestRunnerUtil.getNewArgs(args);
    List<String> classes = new LinkedList<String>();
    HashMap<String, Set<TestPath>> failedTestMap = new HashMap<String, Set<TestPath>>();
    int i = 0;
    List<TestPath> tests = new LinkedList<TestPath>();
    boolean failedUsed = false;
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classes.add(newArgs[i]);
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        tests.add(parseTestPath(newArgs[i]));
        ++i;
      } else if (newArgs[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          String failedClassName = newArgs[i];
          TestPath testPath = parseTestPath(newArgs[i + 1]);
          Set<TestPath> testSet = failedTestMap.get(failedClassName);
          if (testSet == null)
            testSet = new HashSet<TestPath>();
          testSet.add(testPath);
          failedTestMap.put(failedClassName, testSet);
          i += 2;
        }
      } else {
        ++i;
      }
    }

    if (failedUsed) {
      for (String className: failedTestMap.keySet()) {
        runTestSuites(className, failedTestMap.get(className));
      }
    } else {
      for (String className: classes) {
        runTestSuites(className, tests);
      }
    }

    System.exit(0);
  }
}
