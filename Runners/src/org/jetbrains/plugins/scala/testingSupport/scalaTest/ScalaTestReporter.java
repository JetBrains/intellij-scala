package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Reporter;
import org.scalatest.events.*;
import org.scalatest.exceptions.StackDepthException;
import scala.Option;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Stack;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.formatTimestamp;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestReporter implements Reporter {
  public static boolean myShowProgressMessages = true;

  private String getStackTraceString(Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString().trim();
  }

  /**
   * Try to deduce location of test in class. Only works whtn both class name and test name are provided and test name
   * is provided in test definition as a string literal.
   * @param classNameOption option that should contain full qualified class name
   * @param testName name of test under consideration
   * @return location hint in buildserver notation
   */
  private String getLocationHint(Option<String> classNameOption, String testName) {
    if(classNameOption instanceof Some) {
      String className = classNameOption.get();
      return " locationHint='scalatest://Class:" + className + "TestName:" + escapeString(testName) + "'";
    }
    else
      return "";
  }

  private int id = 0;

  private int getCurrentId() {return idStack.peek();}

  private void descend(String message) {
    int parentId = idStack.peek();
    idStack.push(++id);
    waitingScopeMessagesQueue.push("\n##teamcity[" + message + " nodeId='" + getCurrentId() + "' parentNodeId='" + parentId + "']");
  }

  private void onTestStarted() {
    for (String openScopeMessage : waitingScopeMessagesQueue) {
      System.out.println(openScopeMessage);
    }
    waitingScopeMessagesQueue.clear();
  }

  private void ascend(String message) {
    if (waitingScopeMessagesQueue.isEmpty()) {
      //there are no open empty scopes, so scope currently being closed must be not empty, print the actual message
      System.out.println("\n##teamcity[" + message +  "nodeId='" + getCurrentId() + "']");
    } else {
      waitingScopeMessagesQueue.pop();
    }
    idStack.pop();
  }

  private final Stack<Integer> idStack = new Stack<Integer>();
  private final Stack<String> waitingScopeMessagesQueue = new Stack<String>();

  public void apply(Event event) {
    if (event instanceof RunStarting) {
      idStack.clear();
      idStack.push(id);
      RunStarting r = (RunStarting) event;
      int testCount = r.testCount();
      System.out.println("##teamcity[testCount count='" + testCount + "']");
    } else if (event instanceof TestStarting) {
      String testName = ((TestStarting) event).testName();
      String locationHint = getLocationHint(((TestStarting) event).suiteClassName(), testName);
      String message = "testStarted name='" + escapeString(testName) + "'" + locationHint +
          " captureStandardOutput='true'";
      descend(message);
      onTestStarted();
    } else if (event instanceof TestSucceeded) {
      Option<Object> durationOption = ((TestSucceeded) event).duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = (Long) durationOption.get();
      }
      String testName = ((TestSucceeded) event).testName();

      Option<Formatter> formatter = event.formatter();
      if (formatter instanceof Some) {
        if (formatter.get() instanceof IndentedText) {
          IndentedText t = (IndentedText) formatter.get();
          if (myShowProgressMessages) {
            String escaped = escapeString(t.formattedText() + "\n");
            System.out.println("\n##teamcity[message text='" + escaped + "' status='INFO'" + "]");
          }
        }
      }

      String message = "testFinished name='" + escapeString(testName) + "' duration='"+ duration + "'";
      ascend(message);
    } else if (event instanceof TestFailed) {
      boolean error = true;
      Option<Throwable> throwableOption = ((TestFailed) event).throwable();
      String detail = "";
      String failureLocation = "";
      if (throwableOption instanceof Some) {
        Throwable throwable = throwableOption.get();
        if (throwable instanceof AssertionError) error = false;
        detail = getStackTraceString(throwableOption.get());
        if (throwable instanceof StackDepthException) {
          StackDepthException stackDepthException = (StackDepthException) throwable;
          Option<String> fileNameAndLineNumber = stackDepthException.failedCodeFileNameAndLineNumberString();
          if (fileNameAndLineNumber instanceof Some) {
            failureLocation = " (" + fileNameAndLineNumber.get() + ")";
          }
        }
      }
//      Option<Object> durationOption = ((TestFailed) event).duration();
//      long duration = 0;
//      if (durationOption instanceof Some) {
//        duration = (Long) durationOption.get();
//      }
      String testName = ((TestFailed) event).testName();
      String message = ((TestFailed) event).message() + failureLocation;
      long timeStamp = event.timeStamp();
      String res = "testFailed name='" + escapeString(testName) + "' message='" + escapeString(message) +
          "' details='" + escapeString(detail) + "'";
      if (error) res += "error = '" + error + "'";
      res += "timestamp='" + escapeString(formatTimestamp(new Date(timeStamp))) +  "'";
      ascend(res);
    } else if (event instanceof TestIgnored) {
      TestIgnored testIgnored = (TestIgnored) event;
      final String ignoredTestSuffix = "!!! IGNORED !!!";
      String testName = testIgnored.testName() + " " + ignoredTestSuffix;
      String openMessage = "testStarted name='" + escapeString(testName) + "'";
      descend(openMessage);
      String closeMessage = "testIgnored name='" + escapeString(testName) + "' message='" +
          escapeString("Test Ignored") + "'";
      ascend(closeMessage);
    } else if (event instanceof TestPending) {
      String testName = ((TestPending) event).testName();
      String message = "testIgnored name='" + escapeString(testName) + "' message='" +
        escapeString("Test Pending") + "'";
      ascend(message);
      //TODO: should there be TestCanceled processing? It is processed in ScalaTestReporterWithLocation.
    } else if (event instanceof SuiteStarting) {
      String suiteName = ((SuiteStarting) event).suiteName();
      String locationHint = getLocationHint(((SuiteStarting) event).suiteClassName(), suiteName);
      String message = "testSuiteStarted name='" + escapeString(suiteName) + "'" + locationHint +
        " captureStandardOutput='true'";
      descend(message);
    } else if (event instanceof SuiteCompleted) {
      String suiteName = ((SuiteCompleted) event).suiteName();
      String message = "testSuiteFinished name='" + escapeString(suiteName) + "'";
      ascend(message);
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
      Option<Formatter> formatter = event.formatter();
      if (formatter instanceof Some) {
        if (formatter.get() instanceof IndentedText) {
          IndentedText t = (IndentedText) formatter.get();
          message = t.formattedText();
        }
      }
      if (myShowProgressMessages) {
        String escapedMessage = escapeString(message.replaceFirst("\\s+$", ""));
        if (!escapedMessage.isEmpty()) {
          System.out.println("\n##teamcity[message text='" + escapedMessage + ":|n' status='INFO'" + "]");
        }
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
