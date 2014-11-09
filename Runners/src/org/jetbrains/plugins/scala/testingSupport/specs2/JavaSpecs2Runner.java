package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.NotifierRunner;
import testingSupport.specs2.MyNotifierRunner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Runner {

  private static final String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier";

  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException, IOException {
    NotifierRunner runner = new NotifierRunner(new JavaSpecs2Notifier());
    ArrayList<String> specialArgs = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    boolean failedUsed = false;
    ArrayList<String> failedTests = new ArrayList<String>();
    String testName = "";
    boolean showProgressMessages = true;
    int i = 0;
    String[] newArgs  = TestRunnerUtil.getNewArgs(args);
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classes.add(newArgs[i]);
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        testName = newArgs[i];
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
      } else {
        specialArgs.add(newArgs[i]);
        ++i;
      }
    }

    if (failedUsed) {
      i = 0;
      while (i + 1 < failedTests.size()) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(failedTests.get(i), failedTests.get(i + 1), runner, specialArgs);
        i += 2;
      }
    } else if (testName.equals("")) {
      for (String clazz : classes) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(clazz, "", runner, specialArgs);
      }
    } else {
      for (String clazz : classes) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(clazz, testName, runner, specialArgs);
      }
    }
    System.exit(0);
  }

  private static void runSingleTest(String className, String testName, NotifierRunner runner, ArrayList<String> argsArray)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    String specInstantiationMessage = "can not create specification";
    List<String> runnerArgs = new ArrayList<String>();
    runnerArgs.add(className);
    if (!testName.equals("")) runnerArgs.add("-Dspecs2.ex="+ "\"" + testName + "\"");
    runnerArgs.addAll(argsArray);
    Object runnerArgsArray = runnerArgs.toArray(new String[runnerArgs.size()]);
    boolean hasNoStartMethod = false;
    boolean startNotFound = false;

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
          MyNotifierRunner myNotifierRunner = new MyNotifierRunner(new JavaSpecs2Notifier());
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

}
