package org.jetbrains.plugins.scala.testingSupport.uTest;

import scala.Option;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.collection.immutable.Nil$;
import scala.collection.mutable.Buffer;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.runtime.BoxedUnit;
import utest.framework.Result;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner.getClassByFqn;

public class UTestSuiteReflectionRunner extends UTestSuiteRunner {
  private final Method runAsyncMethod;
  private final Class treeSeqClass;
  private final Method testNameMethod;

  public UTestSuiteReflectionRunner(Method runAsyncMethod, Class treeSeqClass) {
    this.runAsyncMethod = runAsyncMethod;
    this.treeSeqClass = treeSeqClass;
    this.testNameMethod = getTestNameMethod();
    assert(this.runAsyncMethod != null);
    assert(this.treeSeqClass != null);
    assert(this.testNameMethod != null);
  }

  private String getTestName(Object child, Method getValue) {
    try {
      return (String) testNameMethod.invoke(getValue.invoke(child));
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public void runTestSuites(String className, Collection<UTestPath> tests, UTestReporter reporter) {
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
    Object treeSeq;

    //collect test data required to perform test launch without scope duplication
    for (UTestPath testPath : testsToRun) {
      testMethods.add(testPath.getMethod());
      Method test = testPath.getMethod();
      Object testTree;
      try {
        testTree = ((Modifier.isStatic(test.getModifiers())) ? test.invoke(null) :
          test.invoke(clazz.getField("MODULE$").get(null)));
      } catch (NoSuchFieldException e) {
        System.out.println("Instance field not found for object " + clazz.getName() + ": " + e.getMessage());
        return;
      } catch (IllegalAccessException e) {
        System.out.println("IllegalAccessException on test initialization for " + clazz.getName() + ": " + e.getMessage());
        return;
      } catch (InvocationTargetException e) {
        System.out.println("InvocationTargetException on test initialization for " + clazz.getName() + ": " + e.getMessage());
        e.printStackTrace();
        return;
      }

      treeSeq = constructTreeSeq(testTree, treeClass, treeSeqClass);
      if (treeSeq == null) {
        System.out.println("Failed to instantiate TestTreeSeq");
        return;
      }

      scala.Tuple2<Buffer<Object>, ?> resolveResult = resolve(treeSeq, testPath, treeSeqClass);
      if (resolveResult == null) {
        System.out.println("Failed to resolve tests tree");
        return;
      }
      Method getChildren = null;

      try {
        getChildren = treeClass.getMethod("children");
        Method getValue = treeClass.getMethod("value");
        traverseTestTreeDown(resolveResult._2(), testPath, leafTests, getChildren, getValue);
      } catch (NoSuchMethodException e) {
        System.out.println("NoSuchMethodExcepton when invokng uTest Tree methods: " + e.getMessage());
        return;
      }
      pathToResolvedTests.put(testPath, resolveResult);
    }
    countTests(childrenCount, leafTests);

    reporter.registerTestClass(className, testMethods.size());

    for (UTestPath testPath : testsToRun) {

      scala.Tuple2<Buffer<Object>, ?> resolveResult = pathToResolvedTests.get(testPath);
      for (UTestPath leafTest : leafTests) {
        //open all leaf tests and their outer scopes
        if (!reporter.isStarted(leafTest)) {
          reporter.reportStarted(leafTest, false);
        }
      }

      treeSeq = constructTreeSeq(resolveResult._2(), treeClass, treeSeqClass);
      if (treeSeq == null) {
        System.out.println("Faled to instantiate treeSeq");
        return;
      }

      invokeRunAsync(treeSeq, resolveResult._1(), runAsyncMethod, reporter, testPath, leafTests,
        childrenCount);
    }
  }

  private Method getTestNameMethod() {
    //is present in older uTest versions, no need for runtime exceptions yet
    try {
      Class testClass = Class.forName("utest.framework.Test");
      return testClass.getMethod("name");
    } catch (ClassNotFoundException e) {
      return null;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private void traverseTestTreeDown(Object testTree, UTestPath currentPath, List<UTestPath> leafTests,
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
        String childTestName;
        childTestName = getTestName(child, getValue);
        traverseTestTreeDown(child, currentPath.append(childTestName), leafTests, getChildren, getValue);
      }
    }
  }

  private static void invokeRunAsync(Object testTree, Buffer<Object> path,
                                     Method runAsyncMethod, final UTestReporter reporter, final UTestPath testPath,
                                     final List<UTestPath> leafTests, final Map<UTestPath, Integer> childrenCount) {
    scala.Function2<Seq<String>, Result, BoxedUnit> reportFunction = getReportFunction(reporter, testPath, leafTests, childrenCount);

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

  private static Class getExecContextRunNowClass() {
    return getClassByFqn("Failed to load ExecutionContext.RunNow from uTest library", "utest.ExecutionContext$RunNow$",
      "utest.framework.ExecutionContext$RunNow$");
  }

  private static Object constructTreeSeq(Object testTree, Class treeClass, Class treeSeqClass) {
    try {
      Constructor<?> treeSeqConstructor = treeSeqClass.getConstructor(treeClass);
      return treeSeqConstructor.newInstance(testTree);
    } catch (NoSuchMethodException e) {
      return null;
    } catch (IllegalAccessException e) {
      return null;
    } catch (InstantiationException e) {
      return null;
    } catch (InvocationTargetException e) {
      return null;
    }
  }

  private static scala.Tuple2<Buffer<Object>, Object> resolve(Object treeSeq, UTestPath testPath, Class treeSeqClass) {
    try {
      Method resolveMethod = treeSeqClass.getMethod("resolve", Seq.class);
      Method defaultParamMethod = treeSeqClass.getMethod("run$default$3");
      return (Tuple2<Buffer<Object>, Object>) resolveMethod.invoke(treeSeq, testPath.getPath().isEmpty() ?
        defaultParamMethod.invoke(treeSeq) : JavaConversions.asScalaBuffer(testPath.getPath()).toList());
    } catch (NoSuchMethodException e) {
      return null;
    } catch (IllegalAccessException e) {
      return null;
    } catch (InvocationTargetException e) {
      return null;
    }
  }
}
