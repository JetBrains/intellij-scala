/*
package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Reporter;
import org.scalatest.events.Event;
import org.scalatest.events.*;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;

import scala.Option;
*/
/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2010
 *//*

public class ScalaTest10Scala28Reporter implements Reporter {
  public void apply(Event event) {
    if (event instanceof RunStarting) {
      RunStarting r = (RunStarting) event;
      runStarting(r.testCount());
    } else if (event instanceof TestStarting) {
      TestStarting t = (TestStarting) event;
      testStarting(t.testName());
    } else if (event instanceof TestSucceeded) {
      TestSucceeded t = (TestSucceeded) event;
      testSucceeded(t.testName(), t.duration());
    } else if (event instanceof TestFailed) {
      TestFailed t = (TestFailed) event;
      testFailed(t.testName(), t.throwable(), t.duration(), t.message(), t.timeStamp());
    } else if (event instanceof TestIgnored) {
      TestIgnored t = (TestIgnored) event;
      testIgnored(t.testName(), "");
    } else if (event instanceof SuiteStarting) {
      SuiteStarting s = (SuiteStarting) event;
      suiteStarting(s.suiteName());
    } else if (event instanceof SuiteCompleted) {
      SuiteCompleted s = (SuiteCompleted) event;
      suiteCompleted(s.suiteName());
    } else {
      //do nothing
    }
  }

  public void testSucceeded(String name, Option<Long> durationOption) {
    long duration;
    if (durationOption.isEmpty()) {
      duration = 0;
    } else {
      duration = (Long) durationOption.get();
    }
    System.out.println("\n##teamcity[testFinished name='" + escapeString(name) + "' duration='"+ duration +"']");
  }

  public void testFailed(String name, Option<Throwable> throwable, Option<Long> duration, String message, long timeStamp) {
    boolean error = true;
    String detail;
    if (!throwable.isEmpty()) {
      Throwable x = (Throwable) throwable.get();
      if (x instanceof AssertionError) error = false;
      StringWriter writer = new StringWriter();
      x.printStackTrace(new PrintWriter(writer));
      detail = writer.getBuffer().toString();
    } else {
      detail = "";
    }
    String res = "\n##teamcity[testFailed name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = '" + error + "'";
    res += "timestamp='" + escapeString("" + timeStamp) + "']";
    System.out.println(res);
    testSucceeded(name, duration);
  }

  public void suiteCompleted(String name) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(name) + "']");
  }

  public void testStarting(String name) {
    System.out.println("\n##teamcity[testStarted name='" + escapeString(name) +
            "' captureStandardOutput='true']");
  }


  public void testIgnored(String name, String message) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) + "']");
  }

  public void suiteStarting(String name) {
    System.out.println("##teamcity[testSuiteStarted name='" + escapeString(name) + "' locationHint='scala://" +
        escapeString(name) + "']");
  }

  public void runStarting(int i) {
    System.out.println("##teamcity[testCount count='" + i + "']");
  }

  private String escapeString(String s) {
    return s.replaceAll("[|]", "||").replaceAll("[']", "|'").replaceAll("[\n]", "|n").replaceAll("[\r]", "|r").replaceAll("]","|]");
  }
}
*/
