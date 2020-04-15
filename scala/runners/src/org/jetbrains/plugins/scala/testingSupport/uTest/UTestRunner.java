package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.plugins.scala.testingSupport.uTest.UTestSuiteRunner.getTreeClass;

public class UTestRunner {

  protected static Class<?> getClassByFqn(String errorMessage, String... options) {
    for (String fqn: options) {
      try {
        return Class.forName(fqn);
      } catch (ClassNotFoundException ignored) {
        // ignore
      }
    }
    throw new RuntimeException(errorMessage);
  }

  private static UTestPath parseTestPath(String className, String argsString) {
    String[] nameArgs = argsString.split("\\\\");
    List<String> asList = Arrays.asList(nameArgs);
    List<String> testPath = asList.subList(1, asList.size());
    Class<?> clazz;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException for " + className + ": " + e.getMessage());
      return null;
    }
    Method method;
    try {
      method = clazz.getMethod(asList.get(0));
      assert (method.getReturnType().equals(getTreeClass()));
    } catch (NoSuchMethodException e) {
      System.out.println("NoSuchMethodException for " + asList.get(0) + " in " + className + ": " + e.getMessage());
      return null;
    }
    return new UTestPath(className, testPath, method);
  }

  public static void main(String[] args) throws IOException {
    String[] newArgs = TestRunnerUtil.getNewArgs(args);
    Map<String, Set<UTestPath>> classesToTests = new HashMap<>();
    HashMap<String, Set<UTestPath>> failedTestMap = new HashMap<>();
    int i = 0;
    String currentClass = null;
    boolean failedUsed = false;
    while (i < newArgs.length) {
      switch (newArgs[i]) {
        case "-s":
          ++i;
          while (i < newArgs.length && !newArgs[i].startsWith("-")) {
            classesToTests.put(newArgs[i], new HashSet<>());
            currentClass = newArgs[i];
            ++i;
          }
          break;
        case "-testName":
          ++i;
          if (currentClass == null)
            throw new RuntimeException("Failed to run tests: no suite class specified for test " + newArgs[i]);
          while (!newArgs[i].startsWith("-")) {
            String testName = newArgs[i];
            UTestPath aTest = parseTestPath(currentClass, testName);
            if (aTest != null) {
              classesToTests.get(currentClass).add(aTest);
            }
            ++i;
          }
          break;
        case "-failedTests":
          failedUsed = true;
          ++i;
          while (i < newArgs.length && !newArgs[i].startsWith("-")) {
            String failedClassName = newArgs[i];
            UTestPath testPath = parseTestPath(failedClassName, newArgs[i + 1]);
            Set<UTestPath> testSet = failedTestMap.get(failedClassName);
            if (testSet == null)
              testSet = new HashSet<>();
            if (testPath != null) {
              testSet.add(testPath);
            }
            failedTestMap.put(failedClassName, testSet);
            i += 2;
          }
          break;
        default:
          ++i;
          break;
      }
    }

    Map<String, Set<UTestPath>> suitesAndTests = failedUsed ? failedTestMap : classesToTests;
    // TODO: (from Nikolay Tropin)
    //  I think it would be better to encapsulate waiting logic in UTestRunner.
    //  Reporter shouldn't be aware about number of tests and manage concurrency.
    UTestReporter reporter = new UTestReporter(suitesAndTests.size());
    UTestSuiteRunner runner = new UTestSuite540Runner();
    for (String className : suitesAndTests.keySet()) {
      runner.runTestSuites(className, suitesAndTests.get(className), reporter);
    }
    reporter.waitUntilReportingFinished();

    System.exit(0);
  }
}
