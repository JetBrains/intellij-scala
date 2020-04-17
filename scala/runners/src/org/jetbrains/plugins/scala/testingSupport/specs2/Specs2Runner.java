package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.ClassRunner$;
import org.specs2.runner.NotifierRunner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Alexander Podkhalyuzin
 */
public class Specs2Runner {

  private static final String REPORTER_FQN = Specs2Notifier.class.getName();

  public static void main(String[] args) throws NoSuchMethodException, IllegalAccessException, IOException {
    final boolean isSpecs2_3;
    try {
      isSpecs2_3 = Spec2VersionUtils.isSpecs2_3();
    } catch (Spec2RunExpectedError spec2RunExpectedError) {
      System.out.println(spec2RunExpectedError.getMessage());
      return;
    }

    ArrayList<String> specialArgs = new ArrayList<>();
    HashMap<String, Set<String>> classesToTests = new HashMap<>();
    String currentClass = null;
    boolean failedUsed = false;
    ArrayList<String> failedTests = new ArrayList<>();
    boolean showProgressMessages = true;
    int i = 0;
    String[] newArgs  = TestRunnerUtil.getNewArgs(args);
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          currentClass = newArgs[i];
          classesToTests.put(currentClass, new HashSet<>());
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        String testNames = newArgs[i];
        String testNamesUnescaped = TestRunnerUtil.unescapeTestName(testNames);
        classesToTests.get(currentClass).add(testNamesUnescaped);
        ++i;
      } else if (newArgs[i].equals("-showProgressMessages")) {
        ++i;
        showProgressMessages = Boolean.parseBoolean(newArgs[i]);
        ++i;
      } else if (newArgs[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          failedTests.add(newArgs[i]);
          ++i;
        }
      } else if (newArgs[i].equals("-C")) {
        ++i;
        String reporterName = newArgs[i];
        if (!reporterName.equals(REPORTER_FQN)) {
          //don't duplicate the reporter
          specialArgs.add("-notifier");
          specialArgs.add(reporterName);
        }
        ++i;
      } else {
        specialArgs.add(newArgs[i]);
        ++i;
      }
    }

    Specs2Notifier notifier = new Specs2Notifier();

    if (failedUsed) {
      i = 0;
      TestRunnerUtil.configureReporter(REPORTER_FQN, showProgressMessages);
      while (i + 1 < failedTests.size()) {
        runSingleTest(failedTests.get(i), failedTests.get(i + 1), isSpecs2_3, specialArgs, notifier);
        i += 2;
      }
    } else {
      TestRunnerUtil.configureReporter(REPORTER_FQN, showProgressMessages);
      for (String className : classesToTests.keySet()) {
        Set<String> tests = classesToTests.get(className);
        runTests(className, tests, isSpecs2_3, specialArgs, notifier);
      }
    }
    System.exit(0);
  }

  private static void runWithNotifierRunner(String[] runnerArgsArray, boolean verbose) {
    final String runnerFQN = "org.specs2.NotifierRunner";

    try {
      ClassRunner$ runner = ClassRunner$.MODULE$;
//      NotifierRunner runner = new NotifierRunner(notifier);
      Method method = runner.getClass().getMethod("run", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      if (verbose) {
        String className = e.getClass().getSimpleName();
        String message = "\n" + className + " for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n";
        System.out.println(message);
      }
    }
  }

  /**
   *
   */
  private static void runSpecs2_old(String[] runnerArgsArray, Specs2Notifier notifier) throws NoSuchMethodException, IllegalAccessException {
    boolean hasNoStartMethod = false;
    boolean startNotFound = false;

    NotifierRunner runner = new NotifierRunner(notifier);

    try {
      Method method = runner.getClass().getMethod("start", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      hasNoStartMethod = true;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      String message = cause.getMessage();
      if (message != null && message.startsWith(specInstantiationMessage)) {
        System.out.println(message);
        return;
      }
      hasNoStartMethod = true;
    }

    if (hasNoStartMethod) {
      try {
        MyNotifierRunner myNotifierRunner = new MyNotifierRunner(notifier);
        Method method = myNotifierRunner.getClass().getMethod("start", String[].class);
        method.invoke(myNotifierRunner, (Object) runnerArgsArray);
      } catch (NoClassDefFoundError e) {
        System.out.println("\nNoClassDefFoundError for 'Start' in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      } catch (NoSuchMethodException e) {
        System.out.println("\nNoSuchMethodException for 'Start' in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        if (message != null && message.startsWith(specInstantiationMessage)) {
          System.out.println(message);
          return;
        }
        System.out.println("\nInvocationTargetException for 'Start' in MyNotifierRunner; cause: " + message + "\n");
        startNotFound = true;
      } catch (IllegalAccessException e) {
        System.out.println("\nIllegalAccessException for 'Start' in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      }
    }

    if (startNotFound) {
      try {
        Method method = runner.getClass().getMethod("main", String[].class);
        method.invoke(runner, (Object) runnerArgsArray);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        if (message != null && message.startsWith(specInstantiationMessage)) {
          System.out.println(message);
        }
      }
    }
  }

  private static void runSpecs2_new(String[] runnerArgsArray) {
    runWithNotifierRunner(runnerArgsArray, true);
  }

  private final static String specInstantiationMessage = "can not create specification";

  private static void runSingleTest(String className,
                                    String testName,
                                    boolean isSpecs2_3,
                                    ArrayList<String> argsArray,
                                    Specs2Notifier notifier)
          throws NoSuchMethodException, IllegalAccessException {
    Collection<String> tests = testName.equals("")
            ? Collections.emptyList()
            : Collections.singletonList(testName);
    runTests(className, tests, isSpecs2_3, argsArray, notifier);
  }

  private static void runTests(String className,
                               Collection<String> tests,
                               boolean isSpecs2_3,
                               ArrayList<String> argsArray,
                               Specs2Notifier notifier)
      throws NoSuchMethodException, IllegalAccessException {
    List<String> runnerArgs = new ArrayList<>();
    runnerArgs.add(className);
    runnerArgs.addAll(argsArray);
    if (isSpecs2_3) {
      //there is a bug with specs2 v3.1+: notifier does not get passed properly through NotifierRunner
      runnerArgs.add("-notifier");
      runnerArgs.add(REPORTER_FQN);
    }
    if (!tests.isEmpty()) {
      runnerArgs.add("-ex");
      runnerArgs.add("\"\\A" + String.join("|", tests) + "\\Z\"");
    }
    String[] runnerArgsArray = runnerArgs.toArray(new String[0]);
    if (isSpecs2_3) {
      runSpecs2_new(runnerArgsArray);
    } else {
      runSpecs2_old(runnerArgsArray, notifier);
    }
  }
}
