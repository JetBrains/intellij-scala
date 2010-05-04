package org.jetbrains.plugins.scala.testingSupport.specs;

import java.util.ArrayList;

import org.specs.Specification;
import org.specs.runner.NotifierRunner;
import org.specs.util.Classes$;
import scala.Option;
import scala.Some;
import scala.reflect.Manifest;
import scala.reflect.Manifest$;

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2010
 */
public class JavaSpecsRunner {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("The first argument should be the specification class name");
      return;
    }
    ArrayList<String> classes  = new ArrayList<String>();
    String sysFilter = ".*";
    String exFilter = ".*";
    int i = 0;
    while (i < args.length) {
      if (args[i].startsWith("-sus:")) {
        sysFilter = args[i].substring(5);
        i = i + 1;
      } else if (args[i].equals("-s")) {
        i = i + 1;
        while (i < args.length && !args[i].startsWith("-")) {
          classes.add(args[i]);
          i = i + 1;
        }
      } else if (args[i].startsWith("-ex:")) {
        exFilter = args[i].substring(4);
        i = i + 1;
      } else {
        i = i + 1;
      }
    }

    Classes$ c = Classes$.MODULE$;
    Manifest$ m = Manifest$.MODULE$;
    Manifest specManifest = m.classType(Specification.class);
    

    for (String clazz: classes) {
      Option<Specification> option = c.createObject(clazz, specManifest);
      if (!option.isEmpty()) {
        runTest(sysFilter, exFilter, option);
      } else {
        option = c.createObject(clazz, specManifest);
        if (!option.isEmpty()) {
          runTest(sysFilter, exFilter, option);
        } else {
          System.out.println("Scala Plugin internal error: no test class was found");
        }
      }
    }
  }

  private static void runTest(final String sysFilter, final String exFilter, Option<Specification> option) {
    try {
      Specification s = option.get();
      NotifierRunner runner = new NotifierRunner(s, new JavaSpecsNotifier()) {
         public String susFilterPattern() {
           return sysFilter;
         }

         public String exampleFilterPattern() {
           return exFilter;
         }
      };
      runner.reportSpecs();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
