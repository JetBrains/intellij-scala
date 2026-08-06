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
 * NOTE: better not to rename this class, just in case. Although the original issue (see commit history)
 * shouldn't be reproducible at this moment, after creating separate notifier for version 2.x
 * <br>
 * see hack in spec2: https://github.com/etorreborre/specs2/commit/7d89a6aa33714ba14b7bf70d9520648b113e7ce8
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

  private final Stack<Integer> idStack = new Stack<>();

  @Override
  public void specStart(@NonNls String title, @NonNls String location) {
    int parentId = descend();
    String message = String.format(
            "testSuiteStarted name='%s'%s nodeId='%d' parentNodeId='%d'",
            escapeString(title), Spec2Utils.parseLocation(location).toHint(), getCurrentId(), parentId
    );
    report(message);
  }

  @Override
  public void specEnd(@NonNls String title, @NonNls String location) {
    report(String.format("testSuiteFinished name='%s' nodeId='%d'", escapeString(title), getCurrentId()));
    ascend();
  }

  @Override
  public void contextStart(@NonNls String text, @NonNls String location) {
    if (text.trim().length() == 0) return;
    int parentId = descend();
    String message = String.format(
            "testSuiteStarted name='%s'%s nodeId='%d' parentNodeId='%d'",
            escapeString(text), Spec2Utils.parseLocation(location).toHint(), getCurrentId(), parentId
    );
    report(message);
    if (myShowProgressMessages) {
      String escapedMessage = escapeString(text.replaceFirst("\\s+$", ""));
      if (!escapedMessage.isEmpty()) {
        report("message text='" + escapedMessage + "|n' status='INFO'");
      }
    }
  }

  @Override
  public void contextEnd(@NonNls String text, @NonNls String location) {
    if (text.trim().length() == 0) return;
    String message = String.format(
            "testSuiteFinished name='%s'%s nodeId='%d'",
            escapeString(text),
            Spec2Utils.parseLocation(location).toHint(), getCurrentId()
    );
    report(message);
    ascend();
  }

  @Override
  public void text(@NonNls String text, @NonNls String location) {
  }

  @Override
  public void exampleStarted(@NonNls String name, @NonNls String location) {
    int parentId = descend();
    report("testStarted name='" + escapeString(name) + "'" + Spec2Utils.parseLocation(location).toHint() +
            " captureStandardOutput='true' nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "'");
  }

  @Override
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

  @Override
  public void exampleFailure(@NonNls String name, @NonNls String message, @NonNls String location, Throwable f, Details details, long duration) {
    String actualExpectedAttrs = TestRunnerUtil.actualExpectedAttrsSpecs2(message, details);
    exampleFailureOrError(name, message, f, false, actualExpectedAttrs);
  }

  @Override
  public void exampleError(@NonNls String name, @NonNls String message, @NonNls String location, Throwable f, long duration) {
    String actualExpectedAttrs = "";
    exampleFailureOrError(name, message, f, true, actualExpectedAttrs);
  }

  @Override
  public void exampleSkipped(@NonNls String name, @NonNls String message, @NonNls String location, long duration) {
    String tsMessage = String.format("testIgnored name='%s' message='%s' nodeId='%d'", escapeString(name), escapeString(message), getCurrentId());
    report(tsMessage);
    ascend();
  }

  @Override
  public void examplePending(@NonNls String name, @NonNls String message, @NonNls String location, long duration) {
    ascend();
  }

  @Override
  public void stepStarted(String location) {
    // TODO
  }

  @Override
  public void stepSuccess(long duration) {
    // TODO
  }

  @Override
  public void stepError(String message, String location, Throwable f, long duration) {
    // TODO
  }

  private void exampleFailureOrError(@NonNls String name, @NonNls String message, Throwable f, boolean error, @NonNls String actualExpectedAttrs) {
    String detail;
    if (f instanceof AssertionError) error = false;
    StringWriter writer = new StringWriter();
    f.printStackTrace(new PrintWriter(writer));
    detail = writer.getBuffer().toString();

    StringBuilder res = new StringBuilder();
    res.append(String.format("testFailed name='%s' message='%s' details='%s'", escapeString(name), escapeString(message), escapeString(detail)));
    if (error)
      res.append("error = 'true'");
    res.append(actualExpectedAttrs);
    res.append(String.format(" timestamp='%s' nodeId='%d'", escapeString(formatCurrentTimestamp()), getCurrentId()));

    report(res.toString());
    ascend();
    //exampleSuccess(name, 0);
  }
}
