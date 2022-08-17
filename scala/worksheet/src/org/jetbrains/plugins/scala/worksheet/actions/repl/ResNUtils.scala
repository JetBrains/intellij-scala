package org.jetbrains.plugins.scala.worksheet.actions.repl

import scala.util.matching.Regex

private[worksheet] object ResNUtils {
  val ResNRegex: Regex = """res\d+""".r
}
