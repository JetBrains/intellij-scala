package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Reporter;
import org.scalatest.events.*;
import scala.Option;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.*;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestReporter implements Reporter {
  private String getStackTraceString(Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }

  public void apply(Event event) {
    if (event instanceof RunStarting) {
      RunStarting r = (RunStarting) event;
      int testCount = r.testCount();
      System.out.println("##teamcity[testCount count='" + testCount + "']");
    } else if (event instanceof TestStarting) {
      String testName = ((TestStarting) event).testName();
      System.out.println("\n##teamcity[testStarted name='" + escapeString(testName) +
            "' captureStandardOutput='true']");
    } else if (event instanceof TestSucceeded) {
      Option<Object> durationOption = ((TestSucceeded) event).duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = ((Long) durationOption.get()).longValue();
      }
      String testName = ((TestSucceeded) event).testName();
      System.out.println("\n##teamcity[testFinished name='" + escapeString(testName) +
          "' duration='"+ duration +"']");
    } else if (event instanceof TestFailed) {
      boolean error = true;
      Option<Throwable> throwableOption = ((TestFailed) event).throwable();
      String detail = "";
      if (throwableOption instanceof Some) {
        if (throwableOption.get() instanceof AssertionError) error = false;
        detail = getStackTraceString(throwableOption.get());
      }
      Option<Object> durationOption = ((TestFailed) event).duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = ((Long) durationOption.get()).longValue();
      }
      String testName = ((TestFailed) event).testName();
      String message = ((TestFailed) event).message();
      long timeStamp = event.timeStamp();
      String res = "\n##teamcity[testFailed name='" + escapeString(testName) + "' message='" + escapeString(message) +
          "' details='" + escapeString(detail) + "'";
      if (error) res += "error = '" + error + "'";
      res += "timestamp='" + escapeString(formatTimestamp(new Date(timeStamp))) + "']";
      System.out.println(res);
      System.out.println("\n##teamcity[testFinished name='" + escapeString(testName) +
          "' duration='" + duration +"']");
    } else if (event instanceof TestIgnored) {
      String testName = ((TestIgnored) event).testName();
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(testName) + "' message='" +
          escapeString("") + "']");
    } else if (event instanceof TestPending) {

    } else if (event instanceof SuiteStarting) {
      String suiteName = ((SuiteStarting) event).suiteName();
      System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "']");
    } else if (event instanceof SuiteCompleted) {
      String suiteName = ((SuiteCompleted) event).suiteName();
      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(suiteName) + "']");
    } else if (event instanceof SuiteAborted) {
      String message = ((SuiteAborted) event).message();
      Option<Throwable> throwableOption = ((SuiteAborted) event).throwable();
      String throwableString = "";
      if (throwableOption instanceof Some) {
        throwableString = " errorDetails='" + escapeString(getStackTraceString(throwableOption.get())) + "'";
      }
      String statusText = "ERROR";
      String escapedMessage = escapeString(message);
      if (!escapedMessage.isEmpty()) {
        System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='" + statusText + "'" +
            throwableString + "]");
      }
    } else if (event instanceof InfoProvided) {
      String message = ((InfoProvided) event).message();
      String escapedMessage = escapeString(message + "\n");
      if (!escapedMessage.isEmpty()) {
        System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='WARNING'" + "]");
      }
    } else if (event instanceof RunStopped) {

    } else if (event instanceof RunAborted) {
      String message = ((RunAborted) event).message();
      Option<Throwable> throwableOption = ((RunAborted) event).throwable();
      String throwableString = "";
      if (throwableOption instanceof Some) {
        throwableString = " errorDetails='" + escapeString(getStackTraceString(throwableOption.get())) + "'";
      }
      String escapedMessage = escapeString(message);
      if (!escapedMessage.isEmpty()) {
        System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='ERROR'" +
            throwableString + "]");
      }
    } else if (event instanceof RunCompleted) {

    }
  }
}
