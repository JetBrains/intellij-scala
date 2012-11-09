package org.jetbrains.plugins.scala.worksheet;

import scala.Some;
import scala.tools.nsc.interpreter.ILoop;

import java.io.BufferedReader;
import java.io.PrintWriter;

/**
 * @author Ksenia.Sautina
 * @since 10/30/12
 */
public class WorksheetInterpreter extends ILoop {
  private BufferedReader _in0;
  private PrintWriter _out;
  private WorksheetReporter intp;
//  private WorksheetReporter reporter = new WorksheetReporter(this);

  public WorksheetInterpreter(BufferedReader in0, PrintWriter out) {
    super(new Some(in0), out);
    _in0 = in0;
    _out = out;
  }

  /** Print a welcome message */
  @Override
  public void printWelcome() {
  }

  @Override
  public void createInterpreter() {
    super.createInterpreter();
    intp = new WorksheetReporter(this);
  }


  @Override
  public void echo(String msg) {
    _out.println("[worksheet]" + msg);
    _out.flush();
  }

  @Override
  public void echoCommandMessage(String msg) {
    super.echoCommandMessage("[worksheetCommand]" + msg);
  }
}
