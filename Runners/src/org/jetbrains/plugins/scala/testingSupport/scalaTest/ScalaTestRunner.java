package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.scalatest.*;
import org.scalatest.tools.Runner;
import scala.None$;
import scala.Option;
import scala.Some$;
import scala.collection.immutable.Map;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
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

  private static void runScalaTest2(String[] args) throws IOException {
    ArrayList<String> argsArray = new ArrayList<String>();
    HashSet<String> classes = new HashSet<String>();
    HashMap<String, Set<String>> failedTestMap = new HashMap<String, Set<String>>();
    boolean failedUsed = false;
    List<String> testNames = new LinkedList<String>();
    boolean showProgressMessages = true;
    boolean useVersionFromOptions = false;
    boolean isOlderScalaVersionFromOptions = false;
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
        testNames.add(TestRunnerUtil.unescapeTestName(newArgs[i]));
        ++i;
      } else if (newArgs[i].equals("-showProgressMessages")) {
        ++i;
        showProgressMessages = Boolean.parseBoolean(newArgs[i]);
        ++i;
      } else if (newArgs[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          String failedClassName = newArgs[i];
          String failedTestName = newArgs[i + 1];
          Set<String> testSet = failedTestMap.get(failedClassName);
          if (testSet == null)
            testSet = new HashSet<String>();
          testSet.add(failedTestName);
          failedTestMap.put(failedClassName, testSet);
          i += 2;
        }
      } else if (newArgs[i].startsWith("-setScalaTestVersion=")) {
        useVersionFromOptions = true;
        isOlderScalaVersionFromOptions = isOlderScalaVersionFromOptions(newArgs[i]);
        ++i;
      } else if (newArgs[i].equals("-C")) {
        if (useVersionFromOptions) {
          argsArray.add(isOlderScalaVersionFromOptions ? "-r" : newArgs[i]);
        } else {
          argsArray.add(isOlderScalaTestVersion() ? "-r" : newArgs[i]);
        }
        if (i + 1 < newArgs.length) argsArray.add(newArgs[i + 1].equals(reporterQualName) ? newArgs[i + 1] + "WithLocation" : newArgs[i + 1]);
        i += 2;
      } else {
        argsArray.add(newArgs[i]);
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

    } else if (testNames.isEmpty()) {
      for (String clazz : classes) {
        argsArray.add("-s");
        argsArray.add(clazz);
      }

    } else {
      //'test' kind of run should only contain one class, better fail then try to run something irrelevant
      assert(classes.size() == 1);
      for (String clazz : classes) {
          for (String tn : testNames) {
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

  private static void runScalaTest1(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, IOException {
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    ArrayList<String> failedTests = new ArrayList<String>();
    boolean failedUsed = false;
    List<String> testNames = new LinkedList<String>();
    boolean showProgressMessages = true;
    boolean useVersionFromOptions = false;
    boolean isOlderScalaVersionFromOptions = false;
    int i = 0;
    int classIndex = 0;
    String[] newArgs = TestRunnerUtil.getNewArgs(args);
    while (i < newArgs.length) {
      if (newArgs[i].equals("-s")) {
        argsArray.add(newArgs[i]);
        ++i;
        argsArray.add("empty");
        classIndex = i;
        while (i < newArgs.length && !newArgs[i].startsWith("-")) {
          classes.add(newArgs[i]);
          ++i;
        }
      } else if (newArgs[i].equals("-testName")) {
        ++i;
        testNames.add(TestRunnerUtil.unescapeTestName(newArgs[i]));
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
      } else if (newArgs[i].startsWith("-setScalaTestVersion=")) {
        useVersionFromOptions = true;
        isOlderScalaVersionFromOptions = isOlderScalaVersionFromOptions(newArgs[i]);
        ++i;
      } else if (newArgs[i].equals("-C")) {
        if (useVersionFromOptions) {
          argsArray.add(isOlderScalaVersionFromOptions ? "-r" : newArgs[i]);
        } else {
          argsArray.add(isOlderScalaTestVersion() ? "-r" : newArgs[i]);
        }
        if (i + 1 < newArgs.length) argsArray.add(newArgs[i + 1]);
        i += 2;
      } else {
        argsArray.add(newArgs[i]);
        ++i;
      }
    }
    String[] arga = argsArray.toArray(new String[argsArray.size()]);
    Class<?> reporterClass = ScalaTestRunner.class.getClassLoader().
        loadClass(reporterQualName);
    Reporter reporter = (Reporter) reporterClass.newInstance();
    if (failedUsed) {
      i = 0;
      while (i + 1 < failedTests.size()) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(failedTests.get(i + 1), failedTests.get(i), reporter);
        i += 2;
      }
    } else if (testNames.isEmpty()) {
      for (String clazz : classes) {
        arga[classIndex] = clazz;
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        Runner.run(arga);
      }
    } else {
      //'test' kind of run should only contain one class, better fail then try to run something irrelevant
      assert(classes.size() == 1);
      for (String clazz : classes) {
        for (String tn : testNames) {
          TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
          runSingleTest(tn, clazz, reporter);
        }
      }
    }
  }


  private static void runSingleTest(String testName, String clazz, Reporter reporter) throws IllegalAccessException,
      InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
    try {
    Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(clazz);
    Suite suite = (Suite) aClass.newInstance();
    Class<?> suiteClass = Class.forName("org.scalatest.Suite");
    Method method = suiteClass.getMethod("run", Option.class, Reporter.class, Stopper.class, org.scalatest.Filter.class,
        Map.class, Option.class, Tracker.class);
    // This stopper could be used to request stop to runner
    Stopper stopper = new Stopper() {
      private volatile boolean stopRequested = false;
      public boolean apply() {
        return stopRequested();
      }
      public boolean stopRequested() {
        return stopRequested;
      }
      public void requestStop() {
        stopRequested = true;
      }
    };
    method.invoke(suite, Some$.MODULE$.apply(testName), reporter, stopper, Filter$.MODULE$.getClass().getMethod("apply").invoke(Filter$.MODULE$),
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
          } else if (Integer.parseInt(nums[0]) == 3 && Integer.parseInt(nums[1]) >= 0) {
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
