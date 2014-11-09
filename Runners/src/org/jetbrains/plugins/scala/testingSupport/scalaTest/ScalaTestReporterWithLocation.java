package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Reporter;
import org.scalatest.events.*;
import scala.Option;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.escapeString;
import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.formatTimestamp;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestReporterWithLocation implements Reporter {
  private String getStackTraceString(Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }

  private String getLocationHint(Option<String> classNameOption, Option locationOption, String testName) {
    if(classNameOption instanceof Some && locationOption instanceof Some) {
      String className = classNameOption.get();
      Object location = locationOption.get();
      if (location instanceof TopOfClass)
        return " locationHint='scalatest://TopOfClass:" + ((TopOfClass) location).className() + "TestName:" + escapeString(testName) + "'";
      else if (location instanceof TopOfMethod) {
        TopOfMethod topOfMethod = (TopOfMethod) location;
        String methodId = topOfMethod.methodId();
        String methodName = methodId.substring(methodId.lastIndexOf('.') + 1, methodId.lastIndexOf('('));
        return " locationHint='scalatest://TopOfMethod:" + topOfMethod.className() + ":" + methodName + "TestName:" + escapeString(testName) + "'";
      }
      else if (location instanceof LineInFile) {
        LineInFile lineInFile = (LineInFile) location;
        return " locationHint='scalatest://LineInFile:" + className + ":" + escapeString(lineInFile.fileName()) +  ":" +
            lineInFile.lineNumber() + "TestName:" + escapeString(testName) + "'";
      }
      else
        return "";
    }
    else
      return "";
  }

  public void apply(Event event) {
    if (event instanceof RunStarting) {
      RunStarting r = (RunStarting) event;
      int testCount = r.testCount();
      System.out.println("##teamcity[testCount count='" + testCount + "']");
    } else if (event instanceof TestStarting) {
      TestStarting testStarting = ((TestStarting) event);
      String testText = testStarting.testText();
      String decodedTestText = decodeString(testText);
      String testName = testStarting.testName();
      String decodedTestName = decodeString(testName);
      String locationHint = getLocationHint(testStarting.suiteClassName(), testStarting.location(), decodedTestName);
      System.out.println("\n##teamcity[testStarted name='" + escapeString(decodedTestText) + "'" + locationHint +
          " captureStandardOutput='true']");
    } else if (event instanceof TestSucceeded) {
      TestSucceeded testSucceeded = (TestSucceeded) event;
      Option<Object> durationOption = testSucceeded.duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = ((java.lang.Long) durationOption.get()).longValue();
      }
      String testText = testSucceeded.testText();
      String decodedTestText = decodeString(testText);
      System.out.println("\n##teamcity[testFinished name='" + escapeString(decodedTestText) +
          "' duration='"+ duration +"']");
      final String testSucceededName = "org.scalatest.events.TestSucceeded";
      collectRecordableEvents(testSucceeded, testSucceededName);
    } else if (event instanceof TestFailed) {
      boolean error = true;
      TestFailed testFailed = (TestFailed) event;
      Option<Throwable> throwableOption = testFailed.throwable();
      String detail = "";
      String locationHint = ""; //todo: ?
      if (throwableOption instanceof Some) {
        Throwable throwable = throwableOption.get();
        if (throwable instanceof AssertionError) error = false;
        detail = getStackTraceString(throwable);
        /*if (throwable instanceof StackDepthException) {
          StackDepthException stackDepthException = (StackDepthException) throwable;
          String className = testFailed.suiteClassName() instanceof Some ? testFailed.suiteClassName().get() : null;
          String fileName = stackDepthException.failedCodeFileName() instanceof Some ? stackDepthException.failedCodeFileName().get() : null;
          Integer lineNumber = stackDepthException.failedCodeLineNumber() instanceof Some ? Integer.parseInt(stackDepthException.failedCodeLineNumber().get().toString()) : null;
          if (className != null && fileName != null && lineNumber != null) {
            locationHint = " locationHint='scalatest://LineInFile:" + className + ":" + fileName + ":" + lineNumber + "'";
          }
        }*/
      }
      Option<Object> durationOption = testFailed.duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = ((java.lang.Long) durationOption.get()).longValue();
      }
      String testText = testFailed.testText();
      String decodedTestText = decodeString(testText);
      String message = testFailed.message();
      long timeStamp = event.timeStamp();
      String res = "\n##teamcity[testFailed name='" + escapeString(decodedTestText) + "' message='" + escapeString(message) +
          "' details='" + escapeString(detail) + "' ";
      if (error) res += "error = '" + error + "'";
      res += "timestamp='" + escapeString(formatTimestamp(new Date(timeStamp))) + "']";
      System.out.println(res);
      System.out.println("\n##teamcity[testFinished name='" + escapeString(decodedTestText) +
          "' duration='" + duration +"' captureStandardOutput='true' " + locationHint + "]");
      final String eventName = "org.scalatest.events.TestFailed";
      collectRecordableEvents(event, eventName);
    } else if (event instanceof TestIgnored) {
      String testText = ((TestIgnored) event).testText();
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(testText) + " (Ignored)' message='" +
          escapeString("Test Ignored") + "']");
    } else if (event instanceof TestPending) {
      TestPending testPending = (TestPending) event;
      String testText = testPending.testText();
      String decodedTestText = decodeString(testText);
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(decodedTestText) + "' message='" +
          escapeString("Test Pending") + "']");
      System.out.println("\n##teamcity[testFinished name='" + escapeString(decodedTestText) +
          "' duration='" + 0 +"']");
      final String eventName = "org.scalatest.events.TestPending";
      collectRecordableEvents(event, eventName);
    } else if (event instanceof TestCanceled) {
      TestCanceled testCanceled = (TestCanceled) event;
      String testText = testCanceled.testText();
      String decodedTestText = decodeString(testText);
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(decodedTestText) + "' message='" +
          escapeString("Test Canceled") + "']");
      System.out.println("\n##teamcity[testFinished name='" + escapeString(decodedTestText) +
          "' duration='" + 0 +"']");
      final String eventName = "org.scalatest.events.TestCancelled";
      collectRecordableEvents(event, eventName);
    } else if (event instanceof SuiteStarting) {
      SuiteStarting suiteStarting = (SuiteStarting) event;
      String suiteName = suiteStarting.suiteName();
      String locationHint = getLocationHint(suiteStarting.suiteClassName(), suiteStarting.location(), suiteName);
      System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "'" + locationHint +
          " captureStandardOutput='true']");
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
    else if(event instanceof ScopeOpened) {
      ScopeOpened scopeOpened = (ScopeOpened) event;
      String message = scopeOpened.message();
      String locationHint = getLocationHint(scopeOpened.nameInfo().suiteClassName(), scopeOpened.location(), message);
      System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(message) + "'" + locationHint +
          " captureStandardOutput='true']");
    }
    else if(event instanceof ScopeClosed) {
      String message = ((ScopeClosed) event).message();
      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(message) + "']");
    }
    else if(event instanceof ScopePending) {
      String message = ((ScopePending) event).message();
      System.out.println("\n##teamcity[testIgnored name='(Scope Pending)' message='" +
          escapeString("Scope Pending") + "']");
      //System.out.println("\n##teamcity[testIgnored name='" + escapeString(message) + "' message='" +
      //    escapeString("Scope Pending") + "']");
      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(message) + "']");
    }
  }

  private void collectRecordableEvents(Event event, String evenQualifiedName) {
    if (hasRecordedEventsMethod(evenQualifiedName)) {
      Class<?> suiteClass;
      try {
        suiteClass = Class.forName(evenQualifiedName);
        final Method recordedEvents = suiteClass.getMethod("recordedEvents");
        final Object invoke = recordedEvents.invoke(event);
        final Method iteratorMethod = invoke.getClass().getMethod("iterator");
        final Object iterator = iteratorMethod.invoke(invoke);
        final Method hasNextMethod = iterator.getClass().getMethod("hasNext");
        final Method nextMethod = iterator.getClass().getMethod("next");
        while ((Boolean) hasNextMethod.invoke(iterator)) {
          Object recordableEvent = nextMethod.invoke(iterator);
          if (recordableEvent instanceof InfoProvided) {
            sendInfoProvided((InfoProvided) recordableEvent);
          }
        }
      } catch (ClassNotFoundException ignore) {
      } catch (NoSuchMethodException ignore) {
      } catch (InvocationTargetException ignore) {
      } catch (IllegalAccessException ignore) {
      }
    }
  }

  private void sendInfoProvided(InfoProvided infoProvided) {
    String message = infoProvided.message();
    String escapedMessage = escapeString(message + "\n");
    if (!escapedMessage.isEmpty()) {
      System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='INFO'" + "]");
    }
  }

  private boolean hasRecordedEventsMethod(String className) {
    try {
      Class<?> suiteClass = Class.forName(className);
      suiteClass.getMethod("recordedEvents");
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  private String decodeString(String input) {
    String output = "";
    try {
      Class<?> nameTransformer = Class.forName("scala.reflect.NameTransformer");
      Method method = nameTransformer.getMethod("decode", String.class);
      output = (String) method.invoke(nameTransformer, input);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } finally {
      if (output.equals("")) {
        output = input;
      }
    }
    return output;
  }
}
