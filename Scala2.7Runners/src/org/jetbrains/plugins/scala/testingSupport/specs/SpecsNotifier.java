package org.jetbrains.plugins.scala.testingSupport.specs;

import org.specs.runner.Notifier;

import java.util.HashMap;
import java.io.StringWriter;
import java.io.PrintWriter;

class SpecsNotifier implements Notifier {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  public void runStarting(int i) {
    System.out.println("##teamcity[testCount count='" + i + "']");
  }

  public void exampleStarting(String s) {
    map.put(s, System.currentTimeMillis());
    System.out.println("\n##teamcity[testStarted name='" + escapeString(s) +
            "' captureStandardOutput='true']");
  }

  public void exampleSucceeded(String s) {
    long duration = System.currentTimeMillis() - map.get(s);
    System.out.println("\n##teamcity[testFinished name='" + escapeString(s) + "' duration='"+ duration +"']");
    map.remove(s);
  }

  public void exampleFailed(String s, Throwable t) {
    boolean error = true;
    String detail;
    if (t instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "\n##teamcity[testFailed name='" + escapeString(s) + "' message='" + escapeString(s) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += "timestamp='" + escapeString(s) + "']";
    System.out.println(res);
    exampleSucceeded(s);
  }

  public void exampleError(String s, Throwable t) {
    exampleFailed(s, t);
  }

  public void exampleSkipped(String s) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(s) + "' message='" + escapeString(s) + "']");
  }

  public void systemStarting(String s) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(s) + "' locationHint='scala://" + escapeString(s) + "']");
  }

  public void systemCompleted(String s) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(s) + "']");
  }

  private String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
  }
}