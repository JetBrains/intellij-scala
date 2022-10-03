package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder.ParallelTreeBuilder;
import org.jetbrains.plugins.scala.testingSupport.scalaTest.treeBuilder.TreeBuilder;
import org.scalatest.Reporter;
import org.scalatest.events.*;
import org.scalatest.exceptions.StackDepthException;
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
 * Reporter for sequential execution of scalaTest test suites. Do not use it with -P key (parallel execution of suites).
 */
public class ScalaTestReporterWithLocation implements Reporter {
    private TreeBuilder treeBuilder = new ParallelTreeBuilder();

    private final static String SCAlA_TEST_URL_PREFIX = "scalatest://";

    public static volatile boolean runAborted = false;

    private String getStackTraceString(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.getBuffer().toString();
    }

    private String getLocationHint(final Option<String> classNameOption,
                                   final Option<? extends Location> locationOption,
                                   final String testName) {
        final String url = getLocationUrl(classNameOption, locationOption, testName);
        return url != null ? " locationHint='" + url + "'" : "";
    }

    private String getLocationUrl(Option<String> classNameOption, Option<? extends Location> locationOption, String testName) {
        String url = null;
        if (classNameOption instanceof Some && locationOption instanceof Some) {
            Object location = locationOption.get();
            if (location instanceof TopOfClass) {
                String className = ((TopOfClass) location).className();
                url = "TopOfClass:" + className +
                        "TestName:" + escapeString(testName);
            } else if (location instanceof TopOfMethod) {
                TopOfMethod topOfMethod = (TopOfMethod) location;
                String methodId = topOfMethod.methodId();
                String methodName = methodId.substring(methodId.lastIndexOf('.') + 1, methodId.lastIndexOf('('));
                String className = topOfMethod.className();
                url = "TopOfMethod:" + className + ":" + methodName +
                        "TestName:" + escapeString(testName);
            } else if (location instanceof LineInFile) {
                LineInFile lineInFile = (LineInFile) location;
                String className = classNameOption.get();
                url =  "LineInFile:" + className + ":" + escapeString(lineInFile.fileName()) + ":" + lineInFile.lineNumber() +
                        "TestName:" + escapeString(testName);
            }
        }
        return url != null ? SCAlA_TEST_URL_PREFIX + url : null;
    }

    @Override
    public void apply(Event event) {
        Ordinal ordinal = event.ordinal();
        if (event instanceof RunStarting) {
            RunStarting r = (RunStarting) event;
            treeBuilder.initRun(r);
            int testCount = r.testCount();
            System.out.println("\n##teamcity[testCount count='" + testCount + "']");
        } else if (event instanceof TestStarting) {
            TestStarting testStarting = ((TestStarting) event);
            String testText = testStarting.testText();
            String decodedTestText = decodeString(testText);
            String testName = testStarting.testName();
            String decodedTestName = decodeString(testName);
            String locationHint = getLocationHint(testStarting.suiteClassName(), testStarting.location(), decodedTestName);
            String message = "testStarted name='" + escapeString(decodedTestText) + "'" + locationHint +
                    " captureStandardOutput='true'";
            treeBuilder.openScope(message, ordinal, testStarting.suiteId(), true);
        } else if (event instanceof TestSucceeded) {
            TestSucceeded testSucceeded = (TestSucceeded) event;
            Option<Object> durationOption = testSucceeded.duration();
            long duration = 0;
            if (durationOption instanceof Some) {
                duration = (Long) durationOption.get();
            }
            String testText = testSucceeded.testText();
            String decodedTestText = decodeString(testText);
            String message = "testFinished name='" + escapeString(decodedTestText) +
                    "' duration='" + duration + "'";
            treeBuilder.closeScope(message, ordinal, testSucceeded.suiteId(), true);
            final String testSucceededName = "org.scalatest.events.TestSucceeded";
            collectRecordableEvents(testSucceeded, testSucceededName);
        } else if (event instanceof TestFailed) {
            boolean error = true;
            TestFailed testFailed = (TestFailed) event;
            Option<Throwable> throwableOption = testFailed.throwable();
            String detail = "";
            String failureLocation = "";
            if (throwableOption instanceof Some) {
                Throwable throwable = throwableOption.get();
                if (throwable instanceof AssertionError) error = false;
                detail = getStackTraceString(throwable);
                if (throwable instanceof StackDepthException) {
                    StackDepthException stackDepthException = (StackDepthException) throwable;
                    Option<String> fileNameAndLineNumber = stackDepthException.failedCodeFileNameAndLineNumberString();
                    StackTraceElement stackTraceElement = (stackDepthException.getStackTrace())[stackDepthException.failedCodeStackDepth()];
                    String className = stackTraceElement != null ? stackTraceElement.getClassName() : null;
                    if (fileNameAndLineNumber instanceof Some && className != null) {
                        failureLocation = "\nScalaTestFailureLocation: " + className + " at (" + fileNameAndLineNumber.get() + ")";
                    }
                }
            }
            String testText = testFailed.testText();
            String decodedTestText = decodeString(testText);
            String message = testFailed.message() + failureLocation;
            long timeStamp = event.timeStamp();
            String res = "testFailed name='" + escapeString(decodedTestText) + "' message='" + escapeString(message) +
                    "' details='" + escapeString(detail) + "' ";
            if (error) res += "error = 'true'";
            res += TestRunnerUtil.actualExpectedAttrsScalaTest(testFailed.message());
            res += "timestamp='" + escapeString(formatTimestamp(new Date(timeStamp))) + "'";
            treeBuilder.closeScope(res, ordinal, testFailed.suiteId(), true);
            final String eventName = "org.scalatest.events.TestFailed";
            collectRecordableEvents(event, eventName);
        } else if (event instanceof TestIgnored) {
            final String ignoredTestSuffix = "!!! IGNORED !!!";
            TestIgnored testIgnored = (TestIgnored) event;
            String testText = testIgnored.testText();
            String decodedTestText = decodeString(testText);
            final String locationHint = getLocationHint(testIgnored.suiteClassName(), testIgnored.location(), decodedTestText);
            String suffixedTestText = decodedTestText + " " + ignoredTestSuffix;
            String openMessage = "testStarted name='" + escapeString(suffixedTestText) + "'" + locationHint;
            treeBuilder.openScope(openMessage, ordinal, testIgnored.suiteId(), true);
            String closeMessage = "testIgnored name='" + escapeString(suffixedTestText) + "' message='" +
                    escapeString("Test Ignored") + "'";
            treeBuilder.closeScope(closeMessage, ordinal, testIgnored.suiteId(), true);
        } else if (event instanceof TestPending) {
            TestPending testPending = (TestPending) event;
            String testText = testPending.testText();
            String decodedTestText = decodeString(testText);
            String message = "testIgnored name='" + escapeString(decodedTestText) + "' message='" +
                    escapeString("Test Pending") + "'";
            treeBuilder.closeScope(message, ordinal, testPending.suiteId(), true);
            final String eventName = "org.scalatest.events.TestPending";
            collectRecordableEvents(event, eventName);
        } else if (event instanceof TestCanceled) {
            TestCanceled testCanceled = (TestCanceled) event;
            String testText = testCanceled.testText();
            String decodedTestText = decodeString(testText);
            Option<Throwable> throwableOption = testCanceled.throwable();
            String throwableStackTrace = null;
            String errorMessage = "";
            if (throwableOption instanceof Some) {
                throwableStackTrace = "\n" + getStackTraceString(throwableOption.get());
                errorMessage = throwableOption.get().getLocalizedMessage();
                errorMessage = errorMessage == null ? "" : ": " + errorMessage;
            }
            String message = "testIgnored name='" + escapeString(decodedTestText) + "' message='" +
                    escapeString("Test Canceled" + errorMessage) + "'" +
                    (throwableStackTrace == null ? "" : " details = '" + escapeString(throwableStackTrace) + "'");
            treeBuilder.closeScope(message, ordinal, testCanceled.suiteId(), true);
            final String eventName = "org.scalatest.events.TestCancelled";
            collectRecordableEvents(event, eventName);
        } else if (event instanceof SuiteStarting) {
            SuiteStarting suiteStarting = (SuiteStarting) event;
            String suiteName = suiteStarting.suiteName();
            String locationHint = getLocationHint(suiteStarting.suiteClassName(), suiteStarting.location(), suiteName);
            String message = "testSuiteStarted name='" + escapeString(suiteName) + "'" + locationHint +
                    " captureStandardOutput='true'";
            treeBuilder.openSuite(message, suiteStarting);
        } else if (event instanceof SuiteCompleted) {
            String suiteName = ((SuiteCompleted) event).suiteName();
            String message = "testSuiteFinished name='" + escapeString(suiteName) + "'";
            treeBuilder.closeSuite(message, (SuiteCompleted) event);
        } else if (event instanceof SuiteAborted) {
            //TODO: see if not processing id stack can cause trouble on suiteAborted
            SuiteAborted suiteAborted = (SuiteAborted) event;
            String message = suiteAborted.message();
            Option<Throwable> throwableOption = suiteAborted.throwable();
            String throwableString = "";
            if (throwableOption instanceof Some) {
                throwableString = " errorDetails='" + escapeString(getStackTraceString(throwableOption.get())) + "'";
            }
            String escapedMessage = escapeString(message);
            if (!escapedMessage.isEmpty()) {
                System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='ERROR'" +
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
                System.out.println("\n##teamcity[message text='" + escapedMessage + "' status='ERROR'" + throwableString + "]");
                runAborted = true;
            }
        } else if (event instanceof RunCompleted) {

        } else if (event instanceof ScopeOpened) {
            ScopeOpened scopeOpened = (ScopeOpened) event;
            String message = scopeOpened.message();
            String locationHint = getLocationHint(scopeOpened.nameInfo().suiteClassName(), scopeOpened.location(), message);
            String tcMessage = "testSuiteStarted name='" + escapeString(message) + "'" + locationHint +
                    " captureStandardOutput='true'";
            treeBuilder.openScope(tcMessage, ordinal, scopeOpened.nameInfo().suiteId(), false);
        } else if (event instanceof ScopeClosed) {
            String message = ((ScopeClosed) event).message();
            String tcMessage = "testSuiteFinished name='" + escapeString(message) + "'";
            treeBuilder.closeScope(tcMessage, ordinal, ((ScopeClosed) event).nameInfo().suiteId(), false);
        } else if (event instanceof ScopePending) {
            String message = ((ScopePending) event).message();
            treeBuilder.closePendingScope(message, ordinal, ((ScopePending) event).nameInfo().suiteId());
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
            } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException ignore) {
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
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            if (output.equals("")) {
                output = input;
            }
        }
        return output;
    }
}
