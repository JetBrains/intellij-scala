package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.execute.Details;
import org.specs2.reporter.Notifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.formatCurrentTimestamp;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Notifier implements Notifier {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  public void specStart(String title, String location) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(title) + "' "+ TestRunnerUtil.parseLocation(location).toHint() + "]");
  }

  public void specEnd(String title, String location) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(title) + "']");
  }

  public void contextStart(String text, String location) {
      System.out.println("##teamcity[testSuiteStarted name='" + escapeString(text) + "' "+ TestRunnerUtil.parseLocation(location).toHint() + "]");
  }

  public void contextEnd(String text, String location) {
    System.out.println("##teamcity[testSuiteFinished name='" + escapeString(text) + "' "+ TestRunnerUtil.parseLocation(location).toHint() + "]");
  }

  public void text(String text, String location) {
  }

  public void exampleStarted(String name, String location) {
    System.out.println("\n##teamcity[testStarted name='" + escapeString(name) + "' " + TestRunnerUtil.parseLocation(location).toHint() +
            " captureStandardOutput='true']");
  }

  public void exampleSuccess(String text, long duration) {
    System.out.println("\n##teamcity[testFinished name='" + escapeString(text) + "' duration='"+ duration +"']");
  }

  // Old API before 23/4/2011. TODO remove
  public void exampleFailure(String name, String message, String location, Throwable f, long duration) {
    exampleFailure(name, message, location, f, null, duration);
  }

  // New API after 23/4/2011
  public void exampleFailure(String name, String message, String location, Throwable f, Details details, long duration) {
    String actualExpectedAttrs = TestRunnerUtil.actualExpectedAttrsSpecs2(message, details);
    exampleFailureOrError(name, message, f, false, actualExpectedAttrs);
  }

  public void exampleError(String name, String message, String location, Throwable f, long duration) {
    String actualExpectedAttrs = "";
    exampleFailureOrError(name, message, f, true, actualExpectedAttrs);
  }

  public void exampleSkipped(String name, String message, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) + "']");
  }

  public void examplePending(String name, String message, long duration) {
  }

  private void exampleFailureOrError(String name, String message, Throwable f, boolean error, String actualExpectedAttrs) {
    String detail;
    if (f instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "\n##teamcity[testFailed name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += actualExpectedAttrs;
    res += " timestamp='" + escapeString(formatCurrentTimestamp()) +  "']";
    System.out.println(res);
    exampleSuccess(name, 0);
  }
}
