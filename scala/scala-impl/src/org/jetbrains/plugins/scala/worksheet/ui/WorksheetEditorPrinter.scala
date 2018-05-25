package org.jetbrains.plugins.scala.worksheet.ui

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Dmitry.Naydanov
  * Date: 25.05.18.
  */
trait WorksheetEditorPrinter {
  def getScalaFile: ScalaFile
  def processLine(line: String): Boolean
  def flushBuffer(): Unit
  def scheduleWorksheetUpdate(): Unit
  def internalError(errorMessage: String)
}
