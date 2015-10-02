package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.execute.Details;
import org.specs2.reporter.Notifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.formatCurrentTimestamp;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Notifier implements Notifier {
  public static boolean myShowProgressMessages = true;

  private static final AtomicInteger id = new AtomicInteger(0);

  private int getCurrentId() {
    return idStack.peek();
  }

  private int descend() {
    if (idStack.isEmpty()) {
      //attach to root
      idStack.push(0);
    }
    int oldId = idStack.peek();
    idStack.push(id.incrementAndGet());
    return oldId;
  }

  private void ascend() {
    idStack.pop();
  }

  private final Stack<Integer> idStack = new Stack<Integer>();

  public void specStart(String title, String location) {
    int parentId = descend();
    System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(title) + "'"+ TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
  }

  public void specEnd(String title, String location) {
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(title) + "' nodeId='" +
        getCurrentId() + "']");
    ascend();
  }

  public void contextStart(String text, String location) {
    if (text.trim().length() == 0) return;
    int parentId = descend();
    System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(text) + "'" + TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
    if (myShowProgressMessages) {
      String escapedMessage = escapeString(text.replaceFirst("\\s+$", ""));
      if (!escapedMessage.isEmpty()) {
        System.out.println("\n##teamcity[message text='" + escapedMessage + "|n' status='INFO'" + "]");
      }
    }
  }

  public void contextEnd(String text, String location) {
    if (text.trim().length() == 0) return;
    System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(text) + "'"+ TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "']");
    ascend();
  }

  public void text(String text, String location) {
  }

  public void exampleStarted(String name, String location) {
    int parentId = descend();
    System.out.println("\n##teamcity[testStarted name='" + escapeString(name) + "'" + TestRunnerUtil.parseLocation(location).toHint() +
            " captureStandardOutput='true' nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
  }

  public void exampleSuccess(String text, long duration) {
    System.out.println("\n##teamcity[testFinished name='" + escapeString(text) + "' duration='"+ duration +
        "' nodeId='" + getCurrentId() + "']");
    ascend();
    if (myShowProgressMessages) {
      String escapedMessage = escapeString(text.replaceFirst("\\s+$", ""));
      if (!escapedMessage.isEmpty()) {
        System.out.println("\n##teamcity[message text='" + escapedMessage + "|n' status='INFO'" + "]");
      }
    }
  }

  public void exampleFailure(String name, String message, String location, Throwable f, Details details, long duration) {
    String actualExpectedAttrs = TestRunnerUtil.actualExpectedAttrsSpecs2(message, details);
    exampleFailureOrError(name, message, f, false, actualExpectedAttrs);
  }

  public void exampleError(String name, String message, String location, Throwable f, long duration) {
    String actualExpectedAttrs = "";
    exampleFailureOrError(name, message, f, true, actualExpectedAttrs);
  }

  public void exampleSkipped(String name, String message, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' nodeId='" + getCurrentId() + "']");
    ascend();
  }

  public void exampleSkipped(String name, String message, String location, long duration) {
    System.out.println("\n##teamcity[testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' nodeId='" + getCurrentId() + "']");
    ascend();
  }

  public void examplePending(String name, String message, String location, long duration) {
    ascend();
  }

  public void examplePending(String name, String message, long duration) {
    ascend();
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
    res += " timestamp='" + escapeString(formatCurrentTimestamp()) +  "' nodeId='" + getCurrentId() + "']";
    System.out.println(res);
    ascend();
    //exampleSuccess(name, 0);
  }
}
