package org.jetbrains.plugins.scala.worksheet.ui.printers

import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetDiffSplitters.SimpleWorksheetSplitter

trait WorksheetEditorPrinter {
  def getScalaFile: ScalaFile
  def processLine(line: String): Boolean
  def flushBuffer(): Unit
  def scheduleWorksheetUpdate(): Unit
  def internalError(errorMessage: String)

  @TestOnly
  def diffSplitter: Option[SimpleWorksheetSplitter]
}
