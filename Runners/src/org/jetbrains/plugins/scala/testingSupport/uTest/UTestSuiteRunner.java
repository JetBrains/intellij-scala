package org.jetbrains.plugins.scala.testingSupport.uTest;

import scala.collection.Seq;
import scala.runtime.BoxedUnit;
import utest.framework.Result;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunner.getClassByFqn;

public abstract class UTestSuiteRunner {
  abstract void runTestSuites(String className, Collection<UTestPath> tests, UTestReporter reporter);

  static Class getTreeClass() {
    return getClassByFqn("Failed to load Tree class from uTest libary.", "utest.util.Tree", "utest.framework.Tree");
  }

  void countTests(Map<UTestPath, Integer> childrenCount, List<UTestPath> leafTests) {
    for (UTestPath leaf: leafTests) {
      traverseTestTreeUp(leaf, childrenCount);
    }
  }

  void traverseTestTreeUp(UTestPath currentPath, Map<UTestPath, Integer> childrenCount) {
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

  static protected scala.Function2<Seq<String>, Result, BoxedUnit> getReportFunction(final UTestReporter reporter,
                                                                                     final UTestPath testPath,
                                                                                     final List<UTestPath> leafTests,
                                                                                     final Map<UTestPath, Integer> childrenCount) {
    return new scala.runtime.AbstractFunction2<Seq<String>, Result, BoxedUnit>() {
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
  }
}
