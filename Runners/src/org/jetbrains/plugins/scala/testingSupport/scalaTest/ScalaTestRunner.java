package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.tools.Runner;

import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTestRunner {
  public static void main(String[] args) {
    ArrayList<String> argsArray = new ArrayList<String>();
    ArrayList<String> classes = new ArrayList<String>();
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
      } else {
        argsArray.add(args[i]);
        ++i;
      }
    }
    String[] arga = argsArray.toArray(new String[argsArray.size()]);
    for (String clazz : classes) {
      arga[classIndex]  = clazz;
      Runner.run(arga);
    }
  }
}
