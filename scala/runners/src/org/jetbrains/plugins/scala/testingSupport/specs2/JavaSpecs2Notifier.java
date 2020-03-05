package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.annotations.NonNls;
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

  private void report(@NonNls String message) {
    System.out.println("\n##teamcity[" + message + "]");
  }

  private final Stack<Integer> idStack = new Stack<Integer>();

  public void specStart(@NonNls String title, @NonNls String location) {
    int parentId = descend();
    report("testSuiteStarted name='" + escapeString(title) + "'"+ TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "'");
  }

  public void specEnd(@NonNls String title, @NonNls String location) {
    report("testSuiteFinished name='" + escapeString(title) + "' nodeId='" +
        getCurrentId() + "'");
    ascend();
  }

  public void contextStart(@NonNls String text, @NonNls String location) {
    if (text.trim().length() == 0) return;
    int parentId = descend();
    report("testSuiteStarted name='" + escapeString(text) + "'" + TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "'");
    if (myShowProgressMessages) {
      String escapedMessage = escapeString(text.replaceFirst("\\s+$", ""));
      if (!escapedMessage.isEmpty()) {
        report("message text='" + escapedMessage + "|n' status='INFO'");
      }
    }
  }

  public void contextEnd(@NonNls String text, @NonNls String location) {
    if (text.trim().length() == 0) return;
    report("testSuiteFinished name='" + escapeString(text) + "'"+ TestRunnerUtil.parseLocation(location).toHint() +
        " nodeId='" + getCurrentId() + "'");
    ascend();
  }

  public void text(@NonNls String text, @NonNls String location) {
  }

  public void exampleStarted(@NonNls String name, @NonNls String location) {
    int parentId = descend();
    report("testStarted name='" + escapeString(name) + "'" + TestRunnerUtil.parseLocation(location).toHint() +
            " captureStandardOutput='true' nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "'");
  }

  public void exampleSuccess(@NonNls String text, long duration) {
    report("testFinished name='" + escapeString(text) + "' duration='"+ duration +
        "' nodeId='" + getCurrentId() + "'");
    ascend();
    if (myShowProgressMessages) {
      String escapedMessage = escapeString(text.replaceFirst("\\s+$", ""));
      if (!escapedMessage.isEmpty()) {
        report("message text='" + escapedMessage + "|n' status='INFO'");
      }
    }
  }

  public void exampleFailure(@NonNls String name, @NonNls String message, @NonNls String location, Throwable f, Details details, long duration) {
    String actualExpectedAttrs = TestRunnerUtil.actualExpectedAttrsSpecs2(message, details);
    exampleFailureOrError(name, message, f, false, actualExpectedAttrs);
  }

  public void exampleError(@NonNls String name, @NonNls String message, @NonNls String location, Throwable f, long duration) {
    String actualExpectedAttrs = "";
    exampleFailureOrError(name, message, f, true, actualExpectedAttrs);
  }

  public void exampleSkipped(@NonNls String name, @NonNls String message, long duration) {
    report("testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' nodeId='" + getCurrentId() + "'");
    ascend();
  }

  public void exampleSkipped(@NonNls String name, @NonNls String message, @NonNls String location, long duration) {
    report("testIgnored name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' nodeId='" + getCurrentId() + "'");
    ascend();
  }

  public void examplePending(@NonNls String name, @NonNls String message, @NonNls String location, long duration) {
    ascend();
  }

  public void examplePending(@NonNls String name, @NonNls String message, long duration) {
    ascend();
  }

  private void exampleFailureOrError(@NonNls String name, @NonNls String message, Throwable f, boolean error, @NonNls String actualExpectedAttrs) {
    String detail;
    if (f instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();
    String res = "testFailed name='" + escapeString(name) + "' message='" + escapeString(message) +
        "' details='" + escapeString(detail) + "'";
    if (error) res += "error = 'true'";
    res += actualExpectedAttrs;
    res += " timestamp='" + escapeString(formatCurrentTimestamp()) +  "' nodeId='" + getCurrentId() + "'";
    report(res);
    ascend();
    //exampleSuccess(name, 0);
  }
}
