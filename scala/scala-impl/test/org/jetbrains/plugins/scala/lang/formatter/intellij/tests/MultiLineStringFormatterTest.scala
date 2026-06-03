package org.jetbrains.plugins.scala.lang.formatter.intellij.tests

import org.jetbrains.plugins.scala.lang.formatter.intellij.FormatterTestBase

import java.nio.file.Path

class MultiLineStringFormatterTest extends FormatterTestBase {
  override protected def relativeTestDataPath: Path = Path.of("formatter", "multiLineStringData")

  override def setUp(): Unit = {
    super.setUp()
    scalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    scalaCodeStyleSettings.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES = false
    scalaCodeStyleSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = true
    commonCodeStyleSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    scalaCodeStyleSettings.MULTILINE_STRING_MARGIN_INDENT = 3
  }
}
