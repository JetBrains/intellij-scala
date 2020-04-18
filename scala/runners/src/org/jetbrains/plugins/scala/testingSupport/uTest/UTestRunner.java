package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.io.IOException;

public class UTestRunner {

  public static void main(String[] argsRaw) throws IOException {
    String[] argsRawFixed = TestRunnerUtil.getNewArgs(argsRaw);
    UTestRunnerArgs args = UTestRunnerArgs.parse(argsRawFixed);

    UTestReporter reporter = new UTestReporter();
    UTestSuiteRunner runner = new UTestSuiteRunner(reporter);
    runner.runTestSuites(args.classesToTests);

    System.exit(0);
  }
}
