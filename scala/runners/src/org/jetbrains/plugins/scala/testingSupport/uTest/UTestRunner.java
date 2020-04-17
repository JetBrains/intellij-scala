package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class UTestRunner {

  public static void main(String[] argsRaw) throws IOException {
    String[] argsRawFixed = TestRunnerUtil.getNewArgs(argsRaw);
    UTestRunnerArgs args = UTestRunnerArgs.parse(argsRawFixed);

    Map<String, Set<UTestPath>> suitesAndTests = args.classesToTests;
    // TODO: (from Nikolay Tropin)
    //  I think it would be better to encapsulate waiting logic in UTestRunner.
    //  Reporter shouldn't be aware about number of tests and manage concurrency.
    UTestReporter reporter = new UTestReporter();
    UTestSuiteRunner runner = new UTestSuiteRunner(reporter);
    runner.runTestSuites(suitesAndTests);

    System.exit(0);
  }
}
