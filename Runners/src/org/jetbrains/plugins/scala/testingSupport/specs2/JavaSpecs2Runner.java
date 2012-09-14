package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.NotifierRunner;
import testingSupport.specs2.MyNotifierRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Runner {

  private static final String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier";

  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException {
    NotifierRunner runner = new NotifierRunner(new JavaSpecs2Notifier());
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> specialArgs = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    boolean failedUsed = false;
    ArrayList<String> failedTests = new ArrayList<String>();
    String testName = "";
    boolean showProgressMessages = true;
    int i = 0;

    while (i < args.length) {
      if (args[i].equals("-s")) {
        argsArray.add(args[i]);
        ++i;
        argsArray.add("empty");
        while (i < args.length && !args[i].startsWith("-")) {
          classes.add(args[i]);
          ++i;
        }
      } else if (args[i].equals("-testName")) {
        ++i;
        testName = args[i];
        specialArgs.add("-Dspecs2.ex="+ "\"" + testName + "\"");
        ++i;
      } else if (args[i].equals("-showProgressMessages")) {
        ++i;
        showProgressMessages = Boolean.parseBoolean(args[i]);
        ++i;
      } else if (args[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < args.length && !args[i].startsWith("-")) {
          failedTests.add(args[i]);
          ++i;
        }
      } else {
        argsArray.add(args[i]);
        specialArgs.add(args[i]);
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
    List<String> runnerArgs = new ArrayList<String>();
    runnerArgs.add(className);
    if (testName != "") runnerArgs.add(testName);
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
        System.out.println("\n'Start' method is not found in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      } catch (NoSuchMethodException e) {
        System.out.println("\n'Start' method is not found in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      } catch (InvocationTargetException e) {
        System.out.println("\n'Start' method is not found in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      } catch (IllegalAccessException e) {
        System.out.println("\n'Start' method is not found in MyNotifierRunner " + e.getMessage() + "\n");
        startNotFound = true;
      }
    }

    if (startNotFound) {
      Method method = runner.getClass().getMethod("main", String[].class);
      method.invoke(runner, runnerArgsArray);
    }
  }
}
