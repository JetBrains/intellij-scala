package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.NotifierRunner;
import testingSupport.specs2.MyNotifierRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> specialArgs = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    boolean failedUsed = false;
    ArrayList<String> failedTests = new ArrayList<String>();
    String testName = "";
    boolean showProgressMessages = true;
    int i = 0;
    String[] newArgs  = getNewArgs(args);
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        argsArray.add(newArgs[i]);
        ++i;
        argsArray.add("empty");
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classes.add(newArgs[i]);
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        testName = newArgs[i];
        specialArgs.add("-Dspecs2.ex="+ "\"" + testName + "\"");
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
        argsArray.add(newArgs[i]);
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

  private static String[] getNewArgs(String[] args) throws IOException {
    String[] newArgs;
    if (args.length == 1 && args[0].startsWith("@")) {
      String arg = args[0];
      File file = new File(arg.substring(1));
      if (!file.exists())
        throw new FileNotFoundException(String.format("argument file %s could not be found", file.getName()));
      FileReader fileReader = new FileReader(file);
      StringBuilder buffer = new StringBuilder();
      while (true) {
        int ind = fileReader.read();
        if (ind == -1) break;
        char c = (char) ind;
        if (c == '\r') continue;
        buffer.append(c);
      }
      newArgs = buffer.toString().split("[\n]");
    } else {
      newArgs = args;
    }
    return newArgs;
  }
}
