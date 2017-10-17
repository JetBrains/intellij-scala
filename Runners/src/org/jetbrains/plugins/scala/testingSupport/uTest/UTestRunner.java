package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import scala.collection.Seq;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.plugins.scala.testingSupport.uTest.UTestSuiteRunner.getTreeClass;

public class UTestRunner {

  protected static Class getClassByFqn(String errorMessage, String... options) {
    for (String fqn: options) {
      try {
        return Class.forName(fqn);
      } catch (ClassNotFoundException ignored) {}
    }
    throw new RuntimeException(errorMessage);
  }

  private static UTestPath parseTestPath(String className, String argsString) {
    String[] nameArgs = argsString.split("\\\\");
    List<String> asList = Arrays.asList(nameArgs);
    List<String> testPath = asList.subList(1, asList.size());
    Class clazz;
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

  private static Method getRunAsynchMethod(Class<?> treeSeqClass) {
    try {
      return treeSeqClass.getMethod("runFuture", scala.Function2.class, Seq.class, Seq.class, scala.concurrent.Future.class, scala.concurrent.ExecutionContext.class);
    } catch (NoSuchMethodException ignored) {}
    try {
      return treeSeqClass.getMethod("runFuture", scala.Function2.class, Seq.class, Seq.class, scala.Function1.class, scala.concurrent.Future.class, scala.concurrent.ExecutionContext.class);
    } catch (NoSuchMethodException ignored) {}
    return null;
  }

  public static void main(String[] args) throws IOException,
          NoSuchFieldException,
          InvocationTargetException,
          IllegalAccessException {
    String[] newArgs = TestRunnerUtil.getNewArgs(args);
    Map<String, Set<UTestPath>> classesToTests = new HashMap<String, Set<UTestPath>>();
    HashMap<String, Set<UTestPath>> failedTestMap = new HashMap<String, Set<UTestPath>>();
    int i = 0;
    String currentClass = null;
    boolean failedUsed = false;
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classesToTests.put(newArgs[i], new HashSet<UTestPath>());
          currentClass = newArgs[i];
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        if (currentClass == null) throw new RuntimeException("Failed to run tests: no suite class specified for test " + newArgs[i]);
        while (!newArgs[i].startsWith("-")) {
          UTestPath aTest = parseTestPath(currentClass, newArgs[i]);
          if (aTest != null) {
            classesToTests.get(currentClass).add(aTest);
          }
          ++i;
        }
      } else if (newArgs[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          String failedClassName = newArgs[i];
          UTestPath testPath = parseTestPath(failedClassName, newArgs[i + 1]);
          Set<UTestPath> testSet = failedTestMap.get(failedClassName);
          if (testSet == null)
            testSet = new HashSet<UTestPath>();
          if (testPath != null) {
            testSet.add(testPath);
          }
          failedTestMap.put(failedClassName, testSet);
          i += 2;
        }
      } else {
        ++i;
      }
    }

    Class treeSeqClass = null;
    Method runAsyncMethod = null;
    try {
      treeSeqClass = Class.forName("utest.framework.TestTreeSeq");
      runAsyncMethod = getRunAsynchMethod(treeSeqClass);
    } catch (ClassNotFoundException e) { }
    Map<String, Set<UTestPath>> suitesAndTests = failedUsed ? failedTestMap : classesToTests;
    UTestReporter reporter = new UTestReporter(suitesAndTests.size());
    UTestSuiteRunner runner = runAsyncMethod != null ? new UTestSuiteReflectionRunner(runAsyncMethod, treeSeqClass) : new UTestSuite540Runner();
    for (String className : suitesAndTests.keySet()) {
      runner.runTestSuites(className, suitesAndTests.get(className), reporter);
    }
    reporter.waitUntilReportingFinished();
    System.exit(0);
  }
}
