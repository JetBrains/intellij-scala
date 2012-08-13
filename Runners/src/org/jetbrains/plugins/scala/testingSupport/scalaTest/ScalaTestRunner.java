package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.scalatest.Filter$;
import org.scalatest.Reporter;
import org.scalatest.Stopper;
import org.scalatest.Suite;
import org.scalatest.Tracker;
import org.scalatest.tools.Runner;
import scala.None$;
import scala.Option;
import scala.Some$;
import scala.collection.immutable.Map;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestRunner {
  private static final String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter";

  public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    try {
      if (isScalaTest2())
        runScalaTest2(args);
      else
        runScalaTest1(args);

    } catch (Throwable ignore) {
      ignore.printStackTrace();
    }

    System.exit(0);
  }

  private static boolean isScalaTest2() {
    try {
      ScalaTestRunner.class.getClassLoader().loadClass("org.scalatest.events.Location");
      return true;
    }
    catch(ClassNotFoundException e) {
      return false;
    }
  }

  private static void runScalaTest2(String[] args) {
    ArrayList<String> argsArray = new ArrayList<String>();
    HashSet<String> classes = new HashSet<String>();
    HashMap<String, Set<String>> failedTestMap = new HashMap<String, Set<String>>();
    boolean failedUsed = false;
    String testName = "";
    boolean showProgressMessages = true;
    boolean useVersionFromOptions = false;
    boolean isOlderScalaVersionFromOptions = false;
    int i = 0;
    while (i < args.length) {
      if (args[i].equals("-s")) {
        ++i;
        while (i < args.length && !args[i].startsWith("-")) {
          classes.add(args[i]);
          ++i;
        }
      } else if (args[i].equals("-testName")) {
        ++i;
        testName = args[i];
        ++i;
      } else if (args[i].equals("-showProgressMessages")) {
        ++i;
        showProgressMessages = Boolean.parseBoolean(args[i]);
        ++i;
      } else if (args[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < args.length && !args[i].startsWith("-")) {
          String failedClassName = args[i];
          String failedTestName = args[i + 1];
          Set<String> testSet = failedTestMap.get(failedClassName);
          if (testSet == null)
            testSet = new HashSet<String>();
          testSet.add(failedTestName);
          failedTestMap.put(failedClassName, testSet);
          i += 2;
        }
      } else if (args[i].startsWith("-setScalaTestVersion=")) {
        useVersionFromOptions = true;
        isOlderScalaVersionFromOptions = isOlderScalaVersionFromOptions(args[i]);
        ++i;
      } else if (args[i].equals("-C")) {
        if (useVersionFromOptions) {
          argsArray.add(isOlderScalaVersionFromOptions ? "-r" : args[i]);
        } else {
          argsArray.add(isOlderScalaTestVersion() ? "-r" : args[i]);
        }
        if (i + 1 < args.length) argsArray.add(args[i + 1] + "WithLocation");
        i += 2;
      } else {
        argsArray.add(args[i]);
        ++i;
      }
    }

    TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
    if (failedUsed) {
      // TODO: How to support -s -i -t here, to support rerunning nested suite's test.
      for (java.util.Map.Entry<String, Set<String>> entry : failedTestMap.entrySet()) {
        argsArray.add("-s");
        argsArray.add(entry.getKey());
        for (String failedTestName : entry.getValue()) {
          argsArray.add("-t");
          argsArray.add(failedTestName);
        }
      }

    } else if (testName.equals("")) {
      for (String clazz : classes) {
        argsArray.add("-s");
        argsArray.add(clazz);
      }

    } else {
      String[] testNames = testName.split(";");
      for (String clazz : classes) {
          for (String tn : Arrays.asList(testNames)) {
          // Should encounter problem if the suite class does not have the specified test name.
          argsArray.add("-s");
          argsArray.add(clazz);

          argsArray.add("-t");
          argsArray.add(tn);
        }
      }
    }
    Runner.run(argsArray.toArray(new String[argsArray.size()]));
  }

  private static void runScalaTest1(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    ArrayList<String> failedTests = new ArrayList<String>();
    boolean failedUsed = false;
    String testName = "";
    boolean showProgressMessages = true;
    boolean useVersionFromOptions = false;
    boolean isOlderScalaVersionFromOptions = false;
    int i = 0;
    int classIndex = 0;
    while (i < args.length) {
      if (args[i].equals("-s")) {
        argsArray.add(args[i]);
        ++i;
        argsArray.add("empty");
        classIndex = i;
        while (i < args.length && !args[i].startsWith("-")) {
          classes.add(args[i]);
          ++i;
        }
      } else if (args[i].equals("-testName")) {
        ++i;
        testName = args[i];
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
      } else if (args[i].startsWith("-setScalaTestVersion=")) {
        useVersionFromOptions = true;
        isOlderScalaVersionFromOptions = isOlderScalaVersionFromOptions(args[i]);
        ++i;
      } else if (args[i].equals("-C")) {
        if (useVersionFromOptions) {
          argsArray.add(isOlderScalaVersionFromOptions ? "-r" : args[i]);
        } else {
          argsArray.add(isOlderScalaTestVersion() ? "-r" : args[i]);
        }
        if (i + 1 < args.length) argsArray.add(args[i + 1]);
        i += 2;
      } else {
        argsArray.add(args[i]);
        ++i;
      }
    }
    String[] arga = argsArray.toArray(new String[argsArray.size()]);
    if (failedUsed) {
      i = 0;
      while (i + 1 < failedTests.size()) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(failedTests.get(i + 1), failedTests.get(i));
        i += 2;
      }
    } else if (testName.equals("")) {
      for (String clazz : classes) {
        arga[classIndex] = clazz;
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        Runner.run(arga);
      }
    } else {
      String[] testNames = testName.split(";");
      for (String clazz : classes) {
        for (String tn : Arrays.asList(testNames)) {
          TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
          runSingleTest(tn, clazz);
        }
      }
    }
  }

  private static void runSingleTest(String testName, String clazz) throws IllegalAccessException,
      InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
    try {
    Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(clazz);
    Suite suite = (Suite) aClass.newInstance();
    String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter";
    Class<?> reporterClass = ScalaTestRunner.class.getClassLoader().
        loadClass(reporterQualName);
    Reporter reporter = (Reporter) reporterClass.newInstance();
    Class<?> suiteClass = Class.forName("org.scalatest.Suite");
    Method method = suiteClass.getMethod("run", Option.class, Reporter.class, Stopper.class, org.scalatest.Filter.class,
        Map.class, Option.class, Tracker.class);
    method.invoke(suite, Some$.MODULE$.apply(testName), reporter, new Stopper() {
      public boolean apply() {
        return false;
      }
    }, Filter$.MODULE$.getClass().getMethod("apply").invoke(Filter$.MODULE$),
        scala.collection.immutable.Map$.MODULE$.empty(), None$.MODULE$, Tracker.class.getConstructor().newInstance());
    }
    catch(Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static boolean isOlderScalaTestVersion() {
    try {
      Class<?> suiteClass = Class.forName("org.scalatest.Suite");
      URL location = suiteClass.getResource('/' + suiteClass.getName().replace('.', '/') + ".class");
      String path = location.getPath();
      String jarPath = path.substring(5, path.indexOf("!"));
      JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
      String version = jar.getManifest().getMainAttributes().getValue("Bundle-Version");
      return parseVersion(version);
    } catch (IOException e) {
      return true;
    } catch (ClassNotFoundException e) {
      return true;
    }
  }

  private static boolean isOlderScalaVersionFromOptions(String arg) {
    if (arg.indexOf("=") + 1 < arg.length()) {
      String version = arg.substring(arg.indexOf("=") + 1);
      return parseVersion(version);
    } else {
      return true;
    }
  }

  private static boolean parseVersion(String version) {
    try {
      if (version != null && !version.isEmpty()) {
        String[] nums = version.split("\\.");
        if (nums.length >= 2) {
          if (Integer.parseInt(nums[0]) == 1 && Integer.parseInt(nums[1]) >= 8) {
            return false;
          } else if (Integer.parseInt(nums[0]) == 2 && Integer.parseInt(nums[1]) >= 0) {
            return false;
          }
        }
      }
    } catch (NumberFormatException e) {
      return true;
    }
    return true;
  }
}
