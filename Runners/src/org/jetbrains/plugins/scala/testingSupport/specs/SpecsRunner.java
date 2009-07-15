package org.jetbrains.plugins.scala.testingSupport.specs;

import org.specs.Specification;
import org.specs.runner.NotifierRunner;
import org.specs.util.Classes$;

import java.util.ArrayList;

import scala.Option;
import scala.Some;

public class SpecsRunner {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("The first argument should be the specification class name");
      return;
    }
/*
    ArrayList<String> classes = new ArrayList<String>();
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

    final String finalSysFilter = sysFilter;
    final String finalExFilter = exFilter;

    for (String clazz : classes) {
      Option spec = Classes$.MODULE$.createObject(clazz);
      if (spec instanceof Some) {
        Specification s = (Specification) (spec.get());
        (new NotifierRunner(s, new SpecsNotifier()) {
          public String susFilterPattern() {
            return finalSysFilter;
          }

          public String exampleFilterPattern() {
            return finalExFilter;
          }
        }).reportSpecs();
      } else {
        spec = Classes$.MODULE$.createObject(clazz + "$");
        if (spec instanceof Some) {
          Specification s = (Specification) (spec.get());
          (new NotifierRunner(s, new SpecsNotifier()) {
            public String susFilterPattern() {
              return finalSysFilter;
            }

            public String exampleFilterPattern() {
              return finalExFilter;
            }
          }).reportSpecs();
        } else {
          System.out.println("Scala Plugin internal error: no test class was found");
        }
      }
    }
*/
  }
}