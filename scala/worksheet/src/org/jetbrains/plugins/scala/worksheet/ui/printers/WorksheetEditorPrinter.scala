package org.jetbrains.plugins.scala.worksheet.ui.printers

import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter

trait WorksheetEditorPrinter {
  def getScalaFile: ScalaFile
  def processLine(line: String): Boolean
  def flushBuffer(): Unit
  def close(): Unit
  def scheduleWorksheetUpdate(): Unit

  /**
   * Handles unexpected exceptions.
   * Exceptions occurred during REPL commands execution are redirected to
   * worksheet standard output, see [[ILoopWrapperFactory#loadReplWrapperAndRun]]
   */
  def internalError(ex: Throwable): Unit

  @TestOnly
  def diffSplitter: Option[SimpleWorksheetSplitter]
}
