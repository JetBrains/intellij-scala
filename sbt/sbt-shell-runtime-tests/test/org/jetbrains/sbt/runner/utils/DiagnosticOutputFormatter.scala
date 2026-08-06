package org.jetbrains.sbt.runner.utils

import java.io.PrintStream

/**
 * Formats diagnostic sections used in SBT run-configuration runtime-test failures.
 *
 * Examples:
 * ```text
 * SBT process output:
 * <empty>
 * ```
 *
 * ```text
 * Run configuration process output:
 * [info] compile
 * ```
 *
 * ```text
 * Run configuration console output:
 * Connected to the target VM
 * Run configuration process output:
 * <empty>
 * ```
 */
private[runner] object DiagnosticOutputFormatter {
  def section(title: String, outputText: String): String =
    s"""$title:
       |${outputOrEmptyPlaceholder(outputText)}""".stripMargin

  def sections(sections: (String, String)*): String =
    sections
      .map { case (title, outputText) => section(title, outputText) }
      .mkString("\n")

  def printSection(title: String, outputText: String, out: PrintStream): Unit =
    out.println(section(title, outputText))

  def outputOrEmptyPlaceholder(outputText: String): String =
    if (outputText.isEmpty) "<empty>" else outputText
}
