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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestRunner {
  private static final String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter";

  public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
    ArrayList<String> failedTests = new ArrayList<String>();
    boolean withLocation = false;
    try {
      Class<?> clazz = ScalaTestRunner.class.getClassLoader().loadClass("org.scalatest.events.Location");
      if (clazz != null) withLocation = true;
    } catch (Throwable ignore) {}
    boolean failedUsed = false;
    String testName = "";
    boolean showProgressMessages = true;
    //TODO: remove. (For support ScalaTest version under 1.8)
    boolean useOlderScalaTestVersion = false;
    boolean isOlderScalaTestVersion = isUseOlderScalaTestVersion();
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
        //TODO: remove. (For support ScalaTest version under 1.8)
      } else if (args[i].equals("-useOlderScalaTestVersion")) {
        ++i;
        useOlderScalaTestVersion = Boolean.parseBoolean(args[i]);
        ++i;
      } else if (args[i].equals("-r") && withLocation && (useOlderScalaTestVersion || isOlderScalaTestVersion)) {
        argsArray.add(args[i]);
        if (i + 1 < args.length) argsArray.add(args[i + 1] + "WithLocation");
        i += 2;
      } else if (args[i].equals("-C") && withLocation && !useOlderScalaTestVersion && !isOlderScalaTestVersion) {
        argsArray.add(args[i]);
        if (i + 1 < args.length) argsArray.add(args[i + 1] + "WithLocation");
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
        runSingleTest(failedTests.get(i + 1), failedTests.get(i), withLocation);
        i += 2;
      }
    } else if (testName.equals("")) {
      for (String clazz : classes) {
        arga[classIndex] = clazz;
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        Runner.run(arga);
      }
    } else {
      for (String clazz : classes) {
        TestRunnerUtil.configureReporter(reporterQualName, showProgressMessages);
        runSingleTest(testName, clazz, withLocation);
      }
    }
    System.exit(0);
  }

  private static void runSingleTest(String testName, String clazz, boolean withLocation) throws IllegalAccessException,
      InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
    Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(clazz);
    Suite suite = (Suite) aClass.newInstance();
    String reporterQualName = "org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestReporter";
    if (withLocation) reporterQualName += "WithLocation";
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
    }, Filter$.MODULE$.apply(),
        scala.collection.immutable.Map$.MODULE$.empty(), None$.MODULE$, new Tracker());
  }

  //TODO: remove. (For support ScalaTest version under 1.8)
  private static boolean isUseOlderScalaTestVersion() throws ClassNotFoundException {
    Class<?> suiteClass = Class.forName("org.scalatest.Suite");
    URL location = suiteClass.getResource('/' + suiteClass.getName().replace('.', '/') + ".class");
    String path = location.getPath();
    if (path.contains("/scalatest") && path.contains(".jar")) {
      int begin = path.indexOf("/scalatest");
      int end = path.indexOf(".jar");
      String jarName = path.substring(begin, end);
      if (jarName.contains("1.")) {
        String version = jarName.substring(jarName.indexOf("1."));
        if (version != null && !version.isEmpty()) {
          String[] nums = version.split("\\.");
          if (nums.length >= 2) {
            if (Integer.parseInt(nums[1]) < 8) {
              return true;
            }
          }
        }
      }
      return false;
    } else {
      return false;
    }
  }
}
