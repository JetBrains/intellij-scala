package org.jetbrains.plugins.scala.testingSupport.uTest;

public class UTestRunner {

  public static void main(String[] argsRaw) {
    UTestRunnerArgs args = UTestRunnerArgs.parse(argsRaw);

    UTestReporter reporter = new UTestReporter();
    UTestSuiteRunner runner = new UTestSuiteRunner(reporter);
    runner.runTestSuites(args.classesToTests);

    System.exit(0);
  }
}
