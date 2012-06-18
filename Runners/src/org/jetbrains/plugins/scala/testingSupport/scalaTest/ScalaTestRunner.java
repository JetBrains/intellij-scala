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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
        if (i + 1 < args.length) argsArray.add(args[i + 1] + (withLocation ? "WithLocation" : ""));
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
