package org.jetbrains.plugins.scala
package lang
package actions
package editor
package enter

import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase

import java.nio.file.Path

class ChainedMethodCallEnterTest extends AbstractEnterActionTestBase {
  override protected def relativeTestDataPath: Path = Path.of("actions", "editor", "enter", "align_method_call_chain")

  override protected def setUp(): Unit = {
    super.setUp()
    commonCodeStyleSettings.getIndentOptions.INDENT_SIZE = 2
    commonCodeStyleSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
  }
}
