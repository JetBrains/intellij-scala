package org.jetbrains.plugins.scala.worksheet.ui.printers

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

trait WorksheetEditorPrinter {
  def getScalaFile: ScalaFile
  def processLine(line: String): Boolean
  def flushBuffer(): Unit
  def scheduleWorksheetUpdate(): Unit
  def internalError(errorMessage: String)
}
