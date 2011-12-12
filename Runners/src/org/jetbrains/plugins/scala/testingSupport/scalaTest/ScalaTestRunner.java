package org.jetbrains.plugins.scala.testingSupport.scalaTest;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestRunner {
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
      } else if (args[i].equals("-failedTests")) {
        failedUsed = true;
        ++i;
        while (i < args.length && !args[i].startsWith("-")) {
          failedTests.add(args[i]);
          ++i;
        }
      } else if (args[i].equals("-r") && withLocation) {
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
        runSingleTest(failedTests.get(i + 1), failedTests.get(i), withLocation);
        i += 2;
      }
    } else if (testName.equals("")) {
      for (String clazz : classes) {
        arga[classIndex] = clazz;
        Runner.run(arga);
      }
    } else {
      for (String clazz : classes) {
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
}
