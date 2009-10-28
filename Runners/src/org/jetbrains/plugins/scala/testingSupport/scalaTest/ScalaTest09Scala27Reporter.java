package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Report;
import org.scalatest.Reporter;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

public class ScalaTest09Scala27Reporter implements Reporter {
  private HashMap<String, Long> map = new HashMap<String, Long>();

  public void testSucceeded(Report r) {
    long duration = System.currentTimeMillis() - map.get(r.name());
    System.out.println("\n##teamcity[testFinished name='" + escapeString(r.name()) + "' duration='"+ duration +"']");
    map.remove(r.name());
  }

  public void testFailed(Report r) {
    long duration = System.currentTimeMillis ()- map.get(r.name());
    boolean error = true;
    String detail;
    if (r.throwable() instanceof Some) {
      Throwable x = (Throwable)((Some) r.throwable()).get();
      if (x instanceof AssertionError) error = false;
      StringWriter writer = new StringWriter();
      x.printStackTrace(new PrintWriter(writer));
      detail = writer.getBuffer().toString();
    } else {
      detail = "";
    }
    String res = "\n##teamcity[testFailed name='" + escapeString(r.name()) + "' message='" + escapeString(r.message()) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += "timestamp='" + escapeString(r.date().toString()) + "']";
    System.out.println(res);
    testSucceeded(r);
  }

  public void suiteCompleted(Report r) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(r.name()) + "']");
  }

  public void testStarting(Report r) {
    map.put(r.name(), System.currentTimeMillis());
    System.out.println("\n##teamcity[testStarted name='" + escapeString(r.name()) +
            "' captureStandardOutput='true']");
  }


  public void testIgnored(Report r) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(r.name()) + "' message='" + escapeString(r.message()) + "']");
  }

  public void suiteStarting(Report r) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(r.name()) + "' locationHint='scala://" +
        escapeString(r.name()) + "']");
  }

  public void suiteAborted(Report r) {

  }

  public void infoProvided(Report r) {

  }

  public void runStopped() {
    
  }

  public void runAborted(Report r) {
    
  }

  public void runCompleted() {
    
  }

  public void dispose() {
    
  }

  public void runStarting(int i) {
    System.out.println("##teamcity[testCount count='" + i + "']");
  }

  private String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
  }
}