package org.jetbrains.plugins.scala.worksheet.actions.repl

import org.jetbrains.plugins.scala.worksheet.WorksheetFile

import scala.util.matching.Regex

private[worksheet] object ResNUtils {
  val ResNRegex: Regex = """res\d+""".r

  def isResNSupportedInFile(file: WorksheetFile): Boolean =
    file.isRepl
}
