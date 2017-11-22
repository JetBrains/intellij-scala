package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.ClassRunner$;
import org.specs2.runner.NotifierRunner;
import org.jetbrains.plugins.scala.testingSupport.specs2.MyNotifierRunner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Runner {

  private static final String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier";

  private static final String classRunnerQualName = "org.specs2.runner.ClassRunner";

  private static boolean isSpecs2_3(Class<?> runnerClass) {
    assert runnerClass != null;
    return runnerClass.isInterface();
  }

  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
    Class<?> runnerClass;
    try {
      runnerClass = Class.forName(classRunnerQualName);
    } catch (ClassNotFoundException e) {
      System.out.println("ClassNotFoundException for " + classRunnerQualName + ": " + e.getMessage());
      return;
    }
    boolean isSpecs2_3 = isSpecs2_3(runnerClass);
    ArrayList<String> specialArgs = new ArrayList<String>();
    HashMap<String, Set<String>> classesToTests = new HashMap<>();
    String currentClass = null;
    boolean failedUsed = false;
    ArrayList<String> failedTests = new ArrayList<String>();
    boolean showProgressMessages = true;
    int i = 0;
    String[] newArgs  = TestRunnerUtil.getNewArgs(args);
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          currentClass = newArgs[i];
          classesToTests.put(currentClass, new HashSet<String>());
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        classesToTests.get(currentClass).add(TestRunnerUtil.unescapeTestName(newArgs[i]));
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
        if (!reporterName.equals(reporterQualName)) {
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

    JavaSpecs2Notifier notifier = new JavaSpecs2Notifier();

    if (failedUsed) {
      i = 0;
      TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
      while (i + 1 < failedTests.size()) {
        runSingleTest(failedTests.get(i), failedTests.get(i + 1), isSpecs2_3, specialArgs, notifier);
        i += 2;
      }
    } else {
      TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
      for (String className : classesToTests.keySet()) {
        Set<String> tests = classesToTests.get(className);
        runTests(className, tests, isSpecs2_3, specialArgs, notifier);
      }
    }
    System.exit(0);
  }

  private static boolean runWithNotifierRunner(Object runnerArgsArray, boolean verbose, JavaSpecs2Notifier notifier) {
    final String runnerFQN = "org.specs2.NotifierRunner";

    try {
      ClassRunner$ runner = ClassRunner$.MODULE$;
//      NotifierRunner runner = new NotifierRunner(notifier);
      Method method = runner.getClass().getMethod("run", String[].class);
      method.invoke(runner, runnerArgsArray);
    } catch (NoSuchMethodException e) {
      if (verbose) {
        System.out.println("\nNoSuchMethodException for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n");
      }
      return false;
    } catch (InvocationTargetException e) {
      if (verbose) {
        System.out.println("\nInvocationTargetException for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n");
      }
      return false;
    } catch (IllegalAccessException e) {
      if (verbose) {
        System.out.println("\nInvocationTargetException for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n");
      }
      return false;
    }
    return true;
  }

  /**
   *
   */
  private static void runSpecs2_old(Object runnerArgsArray, JavaSpecs2Notifier notifier) throws NoSuchMethodException, IllegalAccessException {
    boolean hasNoStartMethod = false;
    boolean startNotFound = false;

    NotifierRunner runner = new NotifierRunner(notifier);

    try {
      Method method = runner.getClass().getMethod("start", String[].class);
      method.invoke(runner, runnerArgsArray);
    } catch (NoSuchMethodException e) {
      hasNoStartMethod = true;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      String message = cause.getMessage();
      if (message != null && message.startsWith(specInstantiationMessage)) {
        System.out.println(message);
        return;
      }
      hasNoStartMethod = true;
    } catch (IllegalAccessException e) {
      hasNoStartMethod = true;
    }

    if (hasNoStartMethod) {
      try {
        MyNotifierRunner myNotifierRunner = new MyNotifierRunner(notifier);
        Method method = myNotifierRunner.getClass().getMethod("start", String[].class);
        method.invoke(myNotifierRunner, runnerArgsArray);
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
        method.invoke(runner, runnerArgsArray);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        if (message != null && message.startsWith(specInstantiationMessage)) {
          System.out.println(message);
        }
      }
    }
  }

  private static void runSpecs2_new(Object runnerArgsArray, JavaSpecs2Notifier notifier) {
    runWithNotifierRunner(runnerArgsArray, true, notifier);
  }

  private final static String specInstantiationMessage = "can not create specification";

  private static void runSingleTest(String className, String testName, boolean isSpecs2_3, ArrayList<String> argsArray, JavaSpecs2Notifier notifier)
          throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    runTests(className, testName.equals("") ? Collections.EMPTY_LIST : Collections.singletonList(testName), isSpecs2_3, argsArray, notifier);
  }

  private static void runTests(String className, Collection<String> tests, boolean isSpecs2_3, ArrayList<String> argsArray, JavaSpecs2Notifier notifier)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    List<String> runnerArgs = new ArrayList<String>();
    runnerArgs.add(className);
    runnerArgs.addAll(argsArray);
    if (isSpecs2_3) {
      //there is a bug with specs2 v3.1+: notifier does not get passed properly through NotifierRunner
      runnerArgs.add("-notifier");
      runnerArgs.add(reporterQualName);
    }
    if (!tests.isEmpty()) {
      runnerArgs.add("-ex");
      runnerArgs.add("\"\\A" + String.join("|", tests) + "\\Z\"");
    }
    Object runnerArgsArray = runnerArgs.toArray(new String[runnerArgs.size()]);
    if (isSpecs2_3) {
      runSpecs2_new(runnerArgsArray, notifier);
    } else {
      runSpecs2_old(runnerArgsArray, notifier);
    }
  }
}
