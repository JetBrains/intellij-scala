package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Filter$;
import org.scalatest.Stopper;
import org.scalatest.Suite;
import org.scalatest.Tracker;
import org.scalatest.tools.Runner;
import scala.Option;
import scala.Some;
import scala.None$;
import scala.Some$;
import scala.Tuple2;
import scala.collection.immutable.List;
import scala.collection.immutable.Map;
import scala.collection.immutable.Set;

import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestRunner {
  public static void main(String[] args) {
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
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
      } else {
        argsArray.add(args[i]);
        ++i;
      }
    }
    String[] arga = argsArray.toArray(new String[argsArray.size()]);
    if (testName.equals("")) {
      for (String clazz : classes) {
        arga[classIndex] = clazz;
        Runner.run(arga);
      }
    } else {
      for (String clazz : classes) {
        try {
          Class<?> aClass = ScalaTestRunner.class.getClassLoader().loadClass(clazz);
          Suite suite = (Suite) aClass.newInstance();
          org.scalatest.Suite$class.run(suite, Some$.MODULE$.apply(testName), new ScalaTestReporter(), new Stopper() {
            public boolean apply() {
              return false;
            }
          }, Filter$.MODULE$.apply(),
          scala.collection.immutable.Map$.MODULE$.empty(), None$.MODULE$, new Tracker());
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (ClassNotFoundException e) {
        }
      }
    }
  }
}
