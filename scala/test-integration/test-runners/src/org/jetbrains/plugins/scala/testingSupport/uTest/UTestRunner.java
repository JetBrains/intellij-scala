package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

public class UTestRunner {

  public static void main(String[] argsRaw) {
    UTestRunnerArgs args = UTestRunnerArgs.parse(TestRunnerUtil.preprocessArgsFiles(argsRaw));

    UTestReporter reporter = new UTestReporter();
    UTestSuiteRunner runner = new UTestSuiteRunner(reporter);
    runner.runTestSuites(args.classesToTests);

    System.exit(0);
  }
}
