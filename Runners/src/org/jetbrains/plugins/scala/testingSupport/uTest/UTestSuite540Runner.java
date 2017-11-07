package org.jetbrains.plugins.scala.testingSupport.uTest;

import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;
import utest.TestRunner;
import utest.Tests;
import utest.framework.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class UTestSuite540Runner extends UTestSuiteRunner {
  public void runTestSuites(String className, Collection<UTestPath> tests, UTestReporter reporter) {
    Collection<UTestPath> testsToRun;
    Class clazz;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException for " + className + ": " + e.getMessage());
      return;
    }
    if (!tests.isEmpty()) {
      testsToRun = tests;
    } else {
      testsToRun = new LinkedList<UTestPath>();
      for (Method method : clazz.getMethods()) {
        if (method.getReturnType().equals(Tests.class) && method.getParameterTypes().length == 0) {
          testsToRun.add(new UTestPath(className, method));
        }
      }
    }
    reporter.reportClassSuiteStarted(className);

    Set<Method> testMethods = new HashSet<Method>();
    List<UTestPath> leafTests = new LinkedList<UTestPath>();
    Map<UTestPath, Integer> childrenCount = new HashMap<UTestPath, Integer>();
    Map<UTestPath, Tests> pathToResolvedTests =
      new HashMap<UTestPath, Tests>();

    //collect test data required to perform test launch without scope duplication
    for (UTestPath testPath : testsToRun) {
      testMethods.add(testPath.getMethod());
      Method test = testPath.getMethod();
      Tests testTree;
      try {
        assert(Modifier.isStatic(test.getModifiers()));
        testTree = (Tests) test.invoke(null);
      } catch (IllegalAccessException e) {
        System.out.println("IllegalAccessException on test initialization for " + clazz.getName() + ": " + e.getMessage());
        return;
      } catch (InvocationTargetException e) {
        System.out.println("InvocationTargetException on test initialization for " + clazz.getName() + ": " + e.getMessage());
        e.printStackTrace();
        return;
      }

      //first, descend to the current path
      Tree<String> current = testTree.nameTree();
      for (String name : testPath.getPath()) {
        boolean changed = false;
        for (scala.collection.Iterator<Tree<String>> it = current.children().iterator(); it.hasNext();) {
          Tree<String> child = it.next();
          if (child.value().equals(name)) {
            current = child;
            changed = true;
            break;
          }
        }
        if (!changed) {
          throw new RuntimeException("Failure in test pathing for test " + testPath);
        }
      }
      traverseTestTreeDown(current, testPath, leafTests);
      pathToResolvedTests.put(testPath, testTree);
    }
    countTests(childrenCount, leafTests);

    reporter.registerTestClass(className, testMethods.size());

    for (UTestPath testPath : testsToRun) {

      Tests resolveResult = pathToResolvedTests.get(testPath);
      for (UTestPath leafTest : leafTests) {
        //open all leaf tests and their outer scopes
        if (!reporter.isStarted(leafTest)) {
          reporter.reportStarted(leafTest, false);
        }
      }

      scala.Function2<Seq<String>, Result, BoxedUnit> reportFunction = getReportFunction(reporter, testPath.getMethodPath(), leafTests, childrenCount);

      Tree<String> subtree = findSubtree(resolveResult.nameTree(), testPath);
      List<Tree<String>> treeList = new LinkedList<Tree<String>>();
      if (subtree != null) {
        treeList.add(subtree);
      }
      TestRunner.runAsync(resolveResult, reportFunction, JavaConverters.asScalaBufferConverter(treeList).asScala(),
        Executor$.MODULE$, ExecutionContext.RunNow$.MODULE$);
    }
  }

  private Tree<String> findSubtree(Tree<String> root, UTestPath path) {
    Tree<String> current = root;
    List<String> walkupNodes = new LinkedList<String>();
    if (path.getPath().isEmpty()) return null;
    for (String node: path.getPath()) {
      scala.collection.Seq<Tree<String>> children = current.children();
      boolean changed = false;
      for (scala.collection.Iterator<Tree<String>> it = children.iterator(); it.hasNext();) {
        Tree<String> check = it.next();
        if (check.value().equals(node)) {
          if (current != root) {
            walkupNodes.add(current.value());
          }
          current = check;
          changed = true;
          break;
        }
      }
      if (!changed) return null;
    }
    Collections.reverse(walkupNodes);
    for (String walkup : walkupNodes) {
      List<Tree<String>> dummyList = new LinkedList<Tree<String>>();
      dummyList.add(current);
      current = new Tree<String>(walkup, JavaConverters.asScalaBufferConverter(dummyList).asScala());
    }
    return current;
  }

  private void traverseTestTreeDown(Tree<String> names, UTestPath currentPath, List<UTestPath> leafTests) {
    //TODO fix this highlighting error
    scala.collection.Seq<Tree<String>> children = names.children();
    if (children.isEmpty()) {
      leafTests.add(currentPath);
    } else {
      for (int i = 0; i < children.size(); i++) {
        Tree<String> child = children.apply(i);
        traverseTestTreeDown(child, currentPath.append(child.value()), leafTests);
      }
    }
  }
}
