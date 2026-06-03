package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.scalatest.*;
import org.scalatest.tools.Runner;
import scala.None$;
import scala.Option;
import scala.Some$;

import java.lang.reflect.Method;
import java.util.*;

public class ScalaTestRunner {

  private static final String REPORTER_FQN = ScalaTestReporter.class.getName();
  private static final String REPORTER_WITH_LOCATION_FQN = ScalaTestReporterWithLocation.class.getName();

  public static void main(String[] argsRaw) {
    int rc = 0;
    try {
      ScalaTestRunnerArgs args = ScalaTestRunnerArgs.parse(TestRunnerUtil.preprocessArgsFiles(argsRaw));

      ScalaTestReporter.myShowProgressMessages = args.showProgressMessages;

      if (ScalaTestVersionUtils.isScalaTest2or3()) {
        runScalaTest2or3(args);
      } else {
        runScalaTest1(args);
      }
      if (ScalaTestReporter.runAborted || ScalaTestReporterWithLocation.runAborted) {
        rc = 2;
      }
    } catch (Throwable e) {
      e.printStackTrace();
      rc = 1;
    }

    System.exit(rc);
  }

  private static void runScalaTest2or3(ScalaTestRunnerArgs args) {
    String[] scalatestLibArgs = toScalatest2or3LibArgs(args);
    Runner.run(scalatestLibArgs);
  }

  /**
   * @return raw arguments passed to ScalaTest internal runner
   * org.scalatest.tools.Runner arguments:
   * http://www.scalatest.org/user_guide/using_the_runner
   */
  private static String[] toScalatest2or3LibArgs(ScalaTestRunnerArgs args) {
    List<String> libArgs = new ArrayList<>(args.otherArgs);

    libArgs.add("-C");
    libArgs.add(REPORTER_WITH_LOCATION_FQN);

    args.classesToTests.forEach((className, tests) -> {
      libArgs.add("-s");
      libArgs.add(className);
      tests.forEach(test -> {
        libArgs.add("-t");
        libArgs.add(test);
      });
    });

    return libArgs.toArray(new String[0]);
  }

  private static void runScalaTest1(ScalaTestRunnerArgs args) {
    if (allTestsAreEmpty(args.classesToTests)) {
      List<String> scalatestArgs = new ArrayList<>(args.otherArgs);
      scalatestArgs.add(ScalaTestVersionUtils.isOldScalaTestVersion() ? "-r" : "-C");
      scalatestArgs.add(REPORTER_FQN);
      String[] scalatestArgsArr = scalatestArgs.toArray(new String[scalatestArgs.size() + 2]);
      scalatestArgsArr[scalatestArgsArr.length - 2] = "-s";
      for (String clazz : args.classesToTests.keySet()) {
        scalatestArgsArr[scalatestArgsArr.length - 1] = clazz;
        Runner.run(scalatestArgsArr);
      }
    } else {
      //'test' kind of run should only contain one class, better fail then try to run something irrelevant
      assert(args.classesToTests.size() == 1);
      Reporter reporter = new ScalaTestReporter();
      Map.Entry<String, Set<String>> entry = args.classesToTests.entrySet().iterator().next();
      String className = entry.getKey();
      Set<String> testNames = entry.getValue();
      for (String testName : testNames) {
        runSingleTest(className, testName, reporter);
      }
    }
  }

  private static boolean allTestsAreEmpty(java.util.Map<String, Set<String>> classesToTests) {
    for (java.util.Map.Entry<String, Set<String>> stringSetEntry : classesToTests.entrySet())
      if (!stringSetEntry.getValue().isEmpty())
        return false;
    return true;
  }

  private static void runSingleTest(String className, String testName, Reporter reporter) {
    try {
      Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(className);
      Suite suite = (Suite) aClass.getDeclaredConstructor().newInstance();
      // This stopper could be used to request stop to runner
      Stopper stopper = new Stopper() {
        private volatile boolean stopRequested = false;
        public boolean apply() {
          return stopRequested();
        }
        @Override
        public boolean stopRequested() {
          return stopRequested;
        }
        @Override
        public void requestStop() {
          stopRequested = true;
        }
      };

      Class<?> baseSuiteClass = Class.forName("org.scalatest.Suite");
      Method method = baseSuiteClass.getMethod(
              "run",
              Option.class,
              Reporter.class,
              Stopper.class,
              org.scalatest.Filter.class,
              scala.collection.immutable.Map.class,
              Option.class,
              Tracker.class
      );
      method.invoke(suite,
              Some$.MODULE$.apply(testName),
              reporter,
              stopper,
              Filter$.MODULE$.getClass().getMethod("apply").invoke(Filter$.MODULE$),
              scala.collection.immutable.Map$.MODULE$.empty(),
              None$.MODULE$,
              Tracker.class.getConstructor().newInstance());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
