package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.specs2.reporter.Notifier;
import org.specs2.reporter.NotifierReporter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Notifier implements Notifier {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  private String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
  }

  public void specStart(String title, String location) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(title) + "' locationHint='scala://" + escapeString(title) + "']");
  }

  public void specEnd(String title, String location) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(title) + "']");
  }

  public void contextStart(String text, String location) {
  }

  public void contextEnd(String text, String location) {
  }

  public void text(String text, String location) {
  }

  public void exampleStarted(String text, String location) {
    System.out.println("\n##teamcity[testStarted name='" + escapeString(text) +
            "' captureStandardOutput='true']");
  }

  public void exampleSuccess(String text, long duration) {
    System.out.println("\n##teamcity[testFinished name='" + escapeString(text) + "' duration='"+ duration +"']");
  }

  public void exampleFailure(String message, Throwable f, long duration) {
    boolean error = true;
    String detail;
    if (f instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "\n##teamcity[testFailed name='" + escapeString(message) + "' message='" + escapeString(message) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += "timestamp='" + escapeString(message) + "']";
    System.out.println(res);
    exampleSuccess(message, 0);
  }

  public void exampleError(String message, Throwable f, long duration) {
    exampleFailure(message, f,  duration);
  }

  public void exampleSkipped(String message, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(message) + "' message='" + escapeString(message) + "']");
  }

  public void examplePending(String message, long duration) {
  }
}
