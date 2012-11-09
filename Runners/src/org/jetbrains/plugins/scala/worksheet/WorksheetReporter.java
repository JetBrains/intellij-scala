package org.jetbrains.plugins.scala.worksheet;

import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.ILoop;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.reporters.ConsoleReporter;

import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */
public class WorksheetReporter extends ConsoleReporter {
  public WorksheetReporter(ILoop intr) {
    super(intr.settings());
  }
  @Override
   public void printMessage(String msg) {
    // Avoiding deadlock if the compiler starts logging before
    // the lazy val is complete.
    super.printMessage("[worksheetReporter]" + msg);
  }

}
