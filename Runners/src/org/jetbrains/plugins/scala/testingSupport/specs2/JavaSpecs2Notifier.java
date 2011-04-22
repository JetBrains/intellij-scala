package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.reporter.Notifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Notifier implements Notifier {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  public void specStart(String title, String location) {
    System.out.println("##teamcity[testSuiteStarted name='" + TestRunnerUtil.escapeString(title) + "' "+ TestRunnerUtil.parseLocation(location).toHint() + "]");
  }

  public void specEnd(String title, String location) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + TestRunnerUtil.escapeString(title) + "']");
  }

  public void contextStart(String text, String location) {
  }

  public void contextEnd(String text, String location) {
  }

  public void text(String text, String location) {
  }

  public void exampleStarted(String name, String location) {
    System.out.println("\n##teamcity[testStarted name='" + TestRunnerUtil.escapeString(name) + "' " + TestRunnerUtil.parseLocation(location).toHint() +
            " captureStandardOutput='true']");
  }

  public void exampleSuccess(String text, long duration) {
    System.out.println("\n##teamcity[testFinished name='" + TestRunnerUtil.escapeString(text) + "' duration='"+ duration +"']");
  }

  public void exampleFailure(String name, String message, String location, Throwable f, long duration) {
    boolean error = true;
    String detail;
    if (f instanceof AssertionError) error = false;
    String actualExpectedAttrs = TestRunnerUtil.actualExpectedAttrsSpecs2(message, f);
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "\n##teamcity[testFailed name='" + TestRunnerUtil.escapeString(name) + "' message='" + TestRunnerUtil.escapeString(message) +
        "' details='" + TestRunnerUtil.escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += actualExpectedAttrs;
    res += " timestamp='" + TestRunnerUtil.escapeString(message) +  "']";
    System.out.println(res);
    exampleSuccess(message, 0);
  }

  public void exampleError(String name, String message, String location, Throwable f, long duration) {
    exampleFailure(name, message, location, f,  duration);
  }

  public void exampleSkipped(String name, String message, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + TestRunnerUtil.escapeString(message) + "' message='" + TestRunnerUtil.escapeString(message) + "']");
  }

  public void examplePending(String name, String message, long duration) {
  }
}
