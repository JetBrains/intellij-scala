package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.specs.JavaSpecsNotifier;
import org.jetbrains.plugins.scala.testingSupport.specs.JavaSpecsRunner;
import org.specs2.runner.NotifierRunner;
import scala.Option;
import scala.reflect.Manifest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaSpecs2Runner {

  private static final String DELIMITER = "--";

  public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    NotifierRunner runner = new NotifierRunner(new JavaSpecs2Notifier());
    List<String> classNames = new ArrayList<String>();
    List<String> specsTestArgs = new ArrayList<String>();
    boolean reachedDelim = false;
    for (String arg : args) {
      if (arg.equals(DELIMITER)) {
        reachedDelim = true;
        specsTestArgs.add(arg);
      } else if (reachedDelim) {
        specsTestArgs.add(arg);
      } else {
        classNames.add(arg);
      }
    }
    for (String className : classNames) {
      List<String> runnerArgs = new ArrayList<String>();
      runnerArgs.add(className);
      runnerArgs.addAll(specsTestArgs);
      Object runnerArgsArray = runnerArgs.toArray(new String[runnerArgs.size()]);
      Method method = runner.getClass().getMethod("main", String[].class);
      method.invoke(runner, runnerArgsArray);
    }
    System.exit(0);
  }
}
