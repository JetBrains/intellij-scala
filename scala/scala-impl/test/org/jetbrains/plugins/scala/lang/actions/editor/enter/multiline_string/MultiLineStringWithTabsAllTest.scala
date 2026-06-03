package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string

import java.nio.file.Path

class MultiLineStringWithTabsAllTest extends MultiLineStringEnterHandlerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "enter", "multiLineStringData", "withTabs", "indentAndMargin", "2tabs")

  override protected def setUp(): Unit = {
    super.setUp()

    scalaCodeStyleSettings.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = true
    scalaCodeStyleSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true
    scalaCodeStyleSettings.MULTILINE_STRING_MARGIN_INDENT = 2

    commonCodeStyleSettings.ALIGN_MULTILINE_BINARY_OPERATION = true

    commonCodeStyleSettings.getIndentOptions.USE_TAB_CHARACTER = true
    commonCodeStyleSettings.getIndentOptions.TAB_SIZE = 2
    commonCodeStyleSettings.getIndentOptions.INDENT_SIZE = 2
    commonCodeStyleSettings.getIndentOptions.CONTINUATION_INDENT_SIZE = 2
  }
}
