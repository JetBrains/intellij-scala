package org.jetbrains.plugins.scala.compiler.rt;

import scala.Some;
import scala.tools.nsc.GenericRunnerCommand;
import scala.tools.nsc.InterpreterLoop;
import scala.tools.nsc.Settings;
import scala.tools.nsc.GenericRunnerSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import scala.Function1;
import scala.Seq;
import scala.runtime.BoxedArray;
import scala.collection.mutable.ListBuffer;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ConsoleRunner {
  public static void main(String[] args) {
    GenericRunnerCommand command = new GenericRunnerCommand(toList(args),
        getSettings(args),
        getUnitFunction());
    Settings settings = command.settings();
    String mkString = "";
    for (String arg : args) {
      mkString += arg + "|";
    }
    if (mkString.length() > 0) mkString = mkString.substring(0, mkString.length() -  1);
    settings.parseParams(mkString, getUnitFunction());
    (new InterpreterLoop(new Some(new BufferedReader(new InputStreamReader(System.in))), new PrintWriter(System.out))).main(settings);
  }

  public static scala.List toList(String[] args) {
    ListBuffer<String> res = new ListBuffer<String>();
    for (String arg : args) {
      res.$plus$eq(arg);
    }
    return res.toList();
  }

  public static Function1 getUnitFunction() {
    return new Function1<String, Void>() {
      public Void apply(String t) {
        System.out.println(t);
        return null;
      }

      public <A> Function1<A, Void> compose(Function1<A, String> f) {
        return f.andThen(this);
      }

      public <A> Function1<String, A> andThen(Function1<Void, A> f) {
        return f.compose(this);
      }
    };
  }

  public static GenericRunnerSettings getSettings(String[] args) {
    return new GenericRunnerSettings(ConsoleRunner.getUnitFunction()) {
      public void parseParams(String line, Function1 error) {
        scala.List args;
        if (line.trim().equals("")) args = toList(new String[0]);
        else {
          String[] newArgs = line.trim().split("[|]");
          for (int i = 0; i < newArgs.length; i++) {
            newArgs[i] = newArgs[i].trim();
          }
          args = toList(newArgs);
        }
        while (!args.isEmpty()) {
          scala.List argsBuf = args;
          if (((String) args.head()).startsWith("-")) {
            scala.List settings = allSettings();
            for (int i = 0; i < settings.length(); ++i) {
              args = ((Settings.Setting) settings.apply(i)).tryToSet(args);
            }
            /*for (setting : allSettings())
              args = setting.tryToSet(args);*/
          }
          else error.apply("Parameter '" + args.head() + "' does not start with '-'.");
          if (argsBuf == args) {
            error.apply("Parameter '" + args.head() + "' is not recognised by Scalac.");
            args = args.tail();
          }
        }
      }
    };
  }
}
