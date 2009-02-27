package org.jetbrains.plugins.scala.compiler.rt;

import scala.Some;
import scala.tools.nsc.GenericRunnerCommand;
import scala.tools.nsc.InterpreterLoop;
import scala.tools.nsc.Settings;
import scala.tools.nsc.GenericRunnerSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ConsoleRunner {
  public static void main(String[] args) {
    //ConsoleRunnerUtil.addQuotes(args);
    GenericRunnerCommand command = new GenericRunnerCommand(ConsoleRunnerUtil.listOf(args),
        ConsoleRunnerUtil.getSettings(args),
        ConsoleRunnerUtil.getUnitFunction());
    Settings settings = command.settings();
    ConsoleRunnerUtil.setParamParser(args, settings);
    (new InterpreterLoop(new Some(new BufferedReader(new InputStreamReader(System.in))), new PrintWriter(System.out))).main(settings);
  }
}
