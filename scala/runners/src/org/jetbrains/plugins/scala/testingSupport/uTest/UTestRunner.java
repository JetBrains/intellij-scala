package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import scala.Option;
import scala.collection.*;
import scala.collection.immutable.Nil$;
import scala.collection.mutable.Buffer;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.runtime.BoxedUnit;
import utest.framework.Result;
import utest.framework.Test;
import utest.framework.TestTreeSeq;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map;
import java.util.Set;

public class UTestRunner {

  private static Class getClassByFqn(String errorMessage, String... options) {
    for (String fqn: options) {
      try {
        return Class.forName(fqn);
      } catch (ClassNotFoundException ignored) {}
    }
    throw new RuntimeException(errorMessage);
  }

  private static Class getTreeClass() {
    return getClassByFqn("Failed to load Test class from uTest libary.", "utest.util.Tree", "utest.framework.Tree");
  }

  private static Class getExecContextRunNowClass() {
    return getClassByFqn("Failed to load ExecutionContext.RunNow from uTest library", "utest.ExecutionContext$RunNow$",
            "utest.framework.ExecutionContext$RunNow$");
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

  private static void traverseTestTreeDown(Object testTree, UTestPath currentPath, List<UTestPath> leafTests,
                                           Method getChildren, Method getValue) {
    Class treeClass = getTreeClass();
    assert(treeClass.isInstance(testTree));
    Seq children;
    try {
      children = (Seq) getChildren.invoke(testTree);
    } catch (IllegalAccessException e) {
      System.out.println("IllegalAccessException when traversing tests tree: " + e);
      return;
    } catch (InvocationTargetException e) {
      System.out.println("InvocationTargetException when traversing tests tree: " + e);
      return;
    }
    if (children.isEmpty()) {
      leafTests.add(currentPath);
    } else {
      for (scala.collection.Iterator it = children.iterator(); it.hasNext();) {
        Object child = it.next();
        Test childTest;
        try {
          childTest = (Test) getValue.invoke(child);
        } catch (IllegalAccessException e) {
          System.out.println("IllegalAccessException when traversing tests tree: " + e);
          return;
        } catch (InvocationTargetException e) {
          System.out.println("InvocationTargetException when traversing tests tree: " + e);
          return;
        }
        traverseTestTreeDown(child, currentPath.append(childTest.name()), leafTests, getChildren, getValue);
      }
    }
  }

  private static void countTests(Map<UTestPath, Integer> childrenCount, List<UTestPath> leafTests) {
    for (UTestPath leaf: leafTests) {
      traverseTestTreeUp(leaf, childrenCount);
    }
  }

  private static void traverseTestTreeUp(UTestPath currentPath, Map<UTestPath, Integer> childrenCount) {
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

  private static void invokeRunAsync(TestTreeSeq testTree, Buffer<Object> path,
                                     Method runAsyncMethod, final UTestReporter reporter, final UTestPath testPath,
                                     final List<UTestPath> leafTests, final Map<UTestPath, Integer> childrenCount) {
    scala.Function2<Seq<String>, Result, BoxedUnit> reportFunction = new scala.runtime.AbstractFunction2<Seq<String>, Result, BoxedUnit>() {
      @Override
      public BoxedUnit apply(Seq < String > seq, Result result) {
        synchronized(reporter) {
          //this is a temporary implementation
          List<String> resSeq = scala.collection.JavaConverters.seqAsJavaList(seq);
          UTestPath resTestPath = testPath.append(resSeq);
          boolean isLeafTest = leafTests.contains(resTestPath);

          if (leafTests.contains(resTestPath)) {
            reporter.reportFinished(resTestPath, result, !isLeafTest, childrenCount);
          }
          return BoxedUnit.UNIT;
        }
      }
    };

    scala.Function1<scala.runtime.AbstractFunction0<scala.concurrent.Future>, scala.concurrent.Future> identity =
            new scala.runtime.AbstractFunction1<scala.runtime.AbstractFunction0<scala.concurrent.Future>, scala.concurrent.Future>() {
              @Override
              public Future apply(scala.runtime.AbstractFunction0<scala.concurrent.Future> v1) {
                return v1.apply();
              }
            };

    String errorMessage = "Failed to invoke " + runAsyncMethod.getName() + ": ";
    Class runNowClass = getExecContextRunNowClass();
    Object runNowDefaultArg = null;
    try {
      runNowDefaultArg = runNowClass.getField("MODULE$").get(runNowClass);
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    try {
      if (runAsyncMethod.getParameterTypes().length == 5) {
        runAsyncMethod.invoke(testTree, reportFunction, path, Nil$.MODULE$, Future$.MODULE$.successful(Option.empty()),
                runNowDefaultArg);
      } else {
        runAsyncMethod.invoke(testTree, reportFunction, path, Nil$.MODULE$, identity,
                Future$.MODULE$.successful(Option.empty()), runNowDefaultArg);
      }
    } catch (IllegalAccessException e) {
      System.out.println(errorMessage + e);
    } catch (InvocationTargetException e) {
      System.out.println(errorMessage + e);
    }
  }

  private static void runTestSuites(String className, Collection<UTestPath> tests, final UTestReporter reporter, Method runAsyncMethod)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Class clazz;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException for " + className + ": " + e.getMessage());
      return;
    }
    Class treeClass = getTreeClass();
    Collection<UTestPath> testsToRun;
    if (!tests.isEmpty()) {
      testsToRun = tests;
    } else {
      testsToRun = new LinkedList<UTestPath>();
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(treeClass) && method.getParameterTypes().length == 0) {
          testsToRun.add(new UTestPath(className, method));
        }
      }
    }
    reporter.reportClassSuiteStarted(className);

    Set<Method> testMethods = new HashSet<Method>();
    List<UTestPath> leafTests = new LinkedList<UTestPath>();
    Map<UTestPath, Integer> childrenCount = new HashMap<UTestPath, Integer>();
    Map<UTestPath, scala.Tuple2<Buffer<Object>, ?>> pathToResolvedTests =
        new HashMap<UTestPath, scala.Tuple2<Buffer<Object>, ?>>();
    TestTreeSeq treeSeq;
    Constructor<TestTreeSeq> treeSeqConstructor = TestTreeSeq.class.getConstructor(treeClass);

    //collect test data required to perform test launch without scope duplication
    for (UTestPath testPath: testsToRun) {
      testMethods.add(testPath.getMethod());
      Method test = testPath.getMethod();
      Object testTree;
      try {
        testTree = ((Modifier.isStatic(test.getModifiers())) ? test.invoke(null) :
            test.invoke(clazz.getField("MODULE$").get(null)));
      } catch (NoSuchFieldException e) {
        System.out.println("Instance field not found for object " + clazz.getName() + ": " + e.getMessage());
        return;
      }

      try {
        treeSeq = treeSeqConstructor.newInstance(testTree);
      } catch (InstantiationException e) {
        System.out.println("Failed to instantiate TestTreeSeq");
        return;
      }

      scala.Tuple2<Buffer<Object>, ?> resolveResult = treeSeq.resolve(testPath.getPath().isEmpty() ?
          treeSeq.run$default$3() : scala.collection.JavaConverters.asScalaBuffer(testPath.getPath()).toList());
      Method getChildren = treeClass.getMethod("children");
      Method getValue = treeClass.getMethod("value");
      traverseTestTreeDown(resolveResult._2(), testPath, leafTests, getChildren, getValue);
      pathToResolvedTests.put(testPath, resolveResult);
    }
    countTests(childrenCount, leafTests);

    reporter.registerTestClass(className, testMethods.size());

    for (UTestPath testPath : testsToRun) {

      scala.Tuple2<Buffer<Object>, ?> resolveResult = pathToResolvedTests.get(testPath);
      for (UTestPath leafTest: leafTests) {
        //open all leaf tests and their outer scopes
        if (!reporter.isStarted(leafTest)) {
          reporter.reportStarted(leafTest, false);
        }
      }

      try {
        treeSeq = treeSeqConstructor.newInstance(resolveResult._2());
      } catch (InstantiationException e) {
        System.out.println("Instance field not found for object " + clazz.getName() + ": " + e.getMessage());
        return;
      }

      invokeRunAsync(treeSeq, resolveResult._1(), runAsyncMethod, reporter, testPath, leafTests,
          childrenCount);
    }
  }


  public static void main(String[] args) throws IOException,
          ClassNotFoundException,
          NoSuchMethodException,
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

    Method runAsyncMethod = getRunAsynchMethod(Class.forName("utest.framework.TestTreeSeq"));
    if (runAsyncMethod != null) {
      Map<String, Set<UTestPath>> suitesAndTests = failedUsed ? failedTestMap : classesToTests;
      UTestReporter reporter = new UTestReporter(suitesAndTests.size());
      for (String className: suitesAndTests.keySet()) {
        runTestSuites(className, suitesAndTests.get(className), reporter, runAsyncMethod);
      }
      reporter.waitUntilReportingFinished();
    } else {
      System.out.println("Failed to locate 'run asynchronous' in utest.framework.TestTreeSeq class.");
    }

    System.exit(0);
  }
}
