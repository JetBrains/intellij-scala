package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.scalatest.*;
import org.scalatest.tools.Runner;
import scala.None$;
import scala.Option;
import scala.Some$;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScalaTestRunner {

  public static final String REPORTER_FQN = ScalaTestReporter.class.getName();
  public static final String REPORTER_WITH_LOCATION_FQN = ScalaTestReporterWithLocation.class.getName();

  public static void main(String[] argsRaw) {
    try {
      String[] argsRawFixed = TestRunnerUtil.getNewArgs(argsRaw);
      ScalaTestRunnerArgs args = ScalaTestRunnerArgs.parse(argsRawFixed);

      if (ScalaTestUtils.isScalaTest2or3()) {
        runScalaTest2or3(args);
      } else {
        runScalaTest1(args);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    System.exit(0);
  }

  private static void runScalaTest2or3(ScalaTestRunnerArgs args) {
    // TODO: WHY USING REFLECTION??? the class is in this module
    TestRunnerUtil.configureReporter(REPORTER_FQN, args.showProgressMessages);

    String[] scalatestLibArgs = toScalatest2or3LibArgs(args);
    Runner.run(scalatestLibArgs);
  }

  /**
   * org.scalatest.tools.Runner arguments:
   * http://www.scalatest.org/user_guide/using_the_runner
   */
  private static String[] toScalatest2or3LibArgs(ScalaTestRunnerArgs args) {
    List<String> scalatestArgs = new ArrayList<>(args.otherArgs);

    // why do we need this if later we setup default reporter? (this was before, I just simplified args construction logic)
    if (args.reporterFqn != null) {
      String reporterFqn = args.reporterFqn.equals(REPORTER_FQN) ? REPORTER_WITH_LOCATION_FQN : args.reporterFqn;
      scalatestArgs.add("-C");
      scalatestArgs.add(reporterFqn);
    }

    args.classesToTests.forEach((className, tests) -> {
      scalatestArgs.add("-s");
      scalatestArgs.add(className);
      tests.forEach(test -> {
        scalatestArgs.add("-t");
        scalatestArgs.add(test);
      });
    });

    return scalatestArgs.toArray(new String[0]);
  }

  private static void runScalaTest1(ScalaTestRunnerArgs args) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    if (allTestsAreEmpty(args.classesToTests)) {
      List<String> scalatestArgs = new ArrayList<>(args.otherArgs);
      // why do we need this if later we setup default reporter? (this was before, I just simplified args construction logic)
      if (args.reporterFqn != null) {
        scalatestArgs.add(ScalaTestUtils.isOldScalaTestVersion() ? "-r" : "-C");
        scalatestArgs.add(args.reporterFqn);
      }
      String[] scalatestArgsArr = scalatestArgs.toArray(new String[scalatestArgs.size() + 2]);
      scalatestArgsArr[scalatestArgsArr.length - 2] = "-s";
      for (String clazz : args.classesToTests.keySet()) {
        scalatestArgsArr[scalatestArgsArr.length - 1] = clazz;
        TestRunnerUtil.configureReporter(REPORTER_FQN, args.showProgressMessages);
        Runner.run(scalatestArgsArr);
      }
    } else {
      // TODO: why need reflection???
      Class<?> reporterClass = ScalaTestRunner.class.getClassLoader().loadClass(REPORTER_FQN);
      Reporter reporter = (Reporter) reporterClass.newInstance();

      //'test' kind of run should only contain one class, better fail then try to run something irrelevant
      assert(args.classesToTests.size() == 1);
      Map.Entry<String, Set<String>> entry = args.classesToTests.entrySet().iterator().next();
      String className = entry.getKey();
      Set<String> testNames = entry.getValue();
      for (String test : testNames) {
        TestRunnerUtil.configureReporter(REPORTER_FQN, args.showProgressMessages);
        runSingleTest(test, className, reporter);
      }
    }
  }

  private static boolean allTestsAreEmpty(java.util.Map<String, Set<String>> classesToTests) {
    for (java.util.Map.Entry<String, Set<String>> stringSetEntry : classesToTests.entrySet())
      if (!stringSetEntry.getValue().isEmpty())
        return false;
    return true;
  }

  private static void runSingleTest(String testName, String clazz, Reporter reporter) {
    try {
    Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(clazz);
    Suite suite = (Suite) aClass.newInstance();
    Class<?> suiteClass = Class.forName("org.scalatest.Suite");
    Method method = suiteClass.getMethod(
        "run",
        Option.class, Reporter.class, Stopper.class, org.scalatest.Filter.class, scala.collection.immutable.Map.class, Option.class, Tracker.class
    );
    // This stopper could be used to request stop to runner
    Stopper stopper = new Stopper() {
      private volatile boolean stopRequested = false;
      public boolean apply() {
        return stopRequested();
      }
      public boolean stopRequested() {
        return stopRequested;
      }
      public void requestStop() {
        stopRequested = true;
      }
    };
      method.invoke(suite,
              Some$.MODULE$.apply(testName),
              reporter,
              stopper,
              Filter$.MODULE$.getClass().getMethod("apply").invoke(Filter$.MODULE$),
              scala.collection.immutable.Map$.MODULE$.empty(),
              None$.MODULE$,
              Tracker.class.getConstructor().newInstance());
    }
    catch(Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
