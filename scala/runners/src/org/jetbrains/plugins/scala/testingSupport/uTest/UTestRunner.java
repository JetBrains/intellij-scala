package org.jetbrains.plugins.scala.testingSupport.uTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class UTestRunner {

  public static void main(String[] argsRaw) throws IOException {
    String[] newArgs = TestRunnerUtil.getNewArgs(argsRaw);
    UTestRunnerArgs args = UTestRunnerArgs.parse(newArgs);

    Map<String, Set<UTestPath>> suitesAndTests = args.classesToTests;
    // TODO: (from Nikolay Tropin)
    //  I think it would be better to encapsulate waiting logic in UTestRunner.
    //  Reporter shouldn't be aware about number of tests and manage concurrency.
    UTestReporter reporter = new UTestReporter(suitesAndTests.size());
    UTestSuiteRunnerBase runner = new UTestSuiteRunner();
    for (String className : suitesAndTests.keySet()) {
      runner.runTestSuites(className, suitesAndTests.get(className), reporter);
    }
    reporter.waitUntilReportingFinished();

    System.exit(0);
  }

  protected static Class<?> getClassByFqn(String errorMessage, String... options) {
    for (String fqn: options) {
      try {
        return Class.forName(fqn);
      } catch (ClassNotFoundException ignored) {
        // ignore
      }
    }
    throw new RuntimeException(errorMessage);
  }
}
