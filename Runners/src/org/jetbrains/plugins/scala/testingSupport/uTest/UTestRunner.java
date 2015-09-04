package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import scala.Option;
import scala.collection.*;
import scala.collection.immutable.Nil$;
import scala.collection.mutable.Buffer;
import scala.concurrent.Future$;
import scala.runtime.BoxedUnit;
import utest.framework.Result;
import utest.framework.Test;
import utest.framework.TestTreeSeq;
import utest.util.Tree;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map;
import java.util.Set;

public class UTestRunner {

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
      assert (method.getReturnType().equals(Tree.class));
    } catch (NoSuchMethodException e) {
      System.out.println("NoSuchMethodException for " + asList.get(0) + " in " + className + ": " + e.getMessage());
      return null;
    }
    return new UTestPath(className, testPath, method);
  }

  private static Method getRunAsynchMethod(Class<?> treeSeqClass) {
    try {
      return treeSeqClass.getMethod("runFuture", scala.Function2.class, Seq.class, Seq.class, scala.concurrent.Future.class, scala.concurrent.ExecutionContext.class);
    } catch (NoSuchMethodException ignored) {

    }
    try {
      return treeSeqClass.getMethod("runAsync", scala.Function2.class, Seq.class, Seq.class, scala.Option.class, scala.concurrent.ExecutionContext.class);
    } catch (NoSuchMethodException ignored) {

    }
    return null;
  }

  private static void traverseTestTreeDown(Tree<Test> testTree, UTestPath currentPath, List<UTestPath> leafTests) {
    if (testTree.children().isEmpty()) {
      leafTests.add(currentPath);
    } else {
      for (scala.collection.Iterator<Tree<Test>> it = testTree.children().iterator(); it.hasNext();) {
        Tree<Test> child = it.next();
        traverseTestTreeDown(child, currentPath.append(child.value().name()), leafTests);
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
          List<String> resSeq = scala.collection.JavaConversions.seqAsJavaList(seq);
          UTestPath resTestPath = testPath.append(resSeq);
          boolean isLeafTest = leafTests.contains(resTestPath);

          if (leafTests.contains(resTestPath)) {
            reporter.reportFinished(resTestPath, result, !isLeafTest, childrenCount);
          }
          return BoxedUnit.UNIT;
        }
      }
    };
    String errorMessage = "Failed to invoke " + runAsyncMethod.getName() + ": ";
    try {
      runAsyncMethod.invoke(testTree, reportFunction, path, Nil$.MODULE$,
          runAsyncMethod.getName().equals("runAsync") ? Option.empty() : Future$.MODULE$.successful(Option.empty()),
          utest.ExecutionContext.RunNow$.MODULE$);
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
    Collection<UTestPath> testsToRun;
    if (!tests.isEmpty()) {
      testsToRun = tests;
    } else {
      testsToRun = new LinkedList<UTestPath>();
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(Tree.class) && method.getParameterTypes().length == 0) {
          testsToRun.add(new UTestPath(className, method));
        }
      }
    }
    reporter.reportClassSuiteStarted(className);

    Set<Method> testMethods = new HashSet<Method>();
    List<UTestPath> leafTests = new LinkedList<UTestPath>();
    Map<UTestPath, Integer> childrenCount = new HashMap<UTestPath, Integer>();
    Map<UTestPath, scala.Tuple2<Buffer<Object>, Tree<Test>>> pathToResolvedTests =
        new HashMap<UTestPath, scala.Tuple2<Buffer<Object>, Tree<Test>>>();
    //collect test data required to perform test launch without scope duplication
    for (UTestPath testPath: testsToRun) {
      testMethods.add(testPath.getMethod());
      Method test = testPath.getMethod();
      Tree<Test> testTree;
      try {
        testTree = (Tree) ((Modifier.isStatic(test.getModifiers())) ? test.invoke(null) :
            test.invoke(clazz.getField("MODULE$").get(null)));
      } catch (NoSuchFieldException e) {
        System.out.println("Instance field not found for object " + clazz.getName() + ": " + e.getMessage());
        return;
      }

      TestTreeSeq treeSeq = new TestTreeSeq(testTree);

      scala.Tuple2<Buffer<Object>, Tree<Test>> resolveResult = treeSeq.resolve(testPath.getPath().isEmpty() ?
          treeSeq.run$default$3() : scala.collection.JavaConversions.asScalaBuffer(testPath.getPath()).toList());
      traverseTestTreeDown(resolveResult._2(), testPath, leafTests);
      pathToResolvedTests.put(testPath, resolveResult);
    }
    countTests(childrenCount, leafTests);

    reporter.registerTestClass(className, testMethods.size());

    for (UTestPath testPath : testsToRun) {

      scala.Tuple2<Buffer<Object>, Tree<Test>> resolveResult = pathToResolvedTests.get(testPath);
      for (UTestPath leafTest: leafTests) {
        //open all leaf tests and their outer scopes
        if (!reporter.isStarted(leafTest)) {
          reporter.reportStarted(leafTest, false);
        }
      }

      invokeRunAsync(new TestTreeSeq(resolveResult._2()), resolveResult._1(), runAsyncMethod, reporter, testPath, leafTests,
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
    List<String> classes = new LinkedList<String>();
    HashMap<String, Set<UTestPath>> failedTestMap = new HashMap<String, Set<UTestPath>>();
    int i = 0;
    List<UTestPath> tests = new LinkedList<UTestPath>();
    String currentClass = null;
    boolean failedUsed = false;
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classes.add(newArgs[i]);
          currentClass = newArgs[i];
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        while (!newArgs[i].startsWith("-")) {
          UTestPath aTest = parseTestPath(currentClass, newArgs[i]);
          if (aTest != null) {
            tests.add(aTest);
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
      Collection<String> classNames = failedUsed ? failedTestMap.keySet() : classes;

      UTestReporter reporter = new UTestReporter(classNames.size());
      for (String className: classNames) {
        runTestSuites(className, failedUsed ? failedTestMap.get(className) : tests, reporter, runAsyncMethod);
      }
      reporter.waitUntilReportingFinished();
    } else {
      System.out.println("Failed to locate 'run asynchronous' in utest.framework.TestTreeSeq class.");
    }

    System.exit(0);
  }
}
