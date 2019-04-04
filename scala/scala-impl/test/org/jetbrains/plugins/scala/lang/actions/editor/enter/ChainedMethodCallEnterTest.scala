package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.lang.actions.editor.enter.ChainedMethodCallEnterTest.DATA_PATH
import org.junit.runner.RunWith
import org.junit.runners.AllTests

@RunWith(classOf[AllTests])
class ChainedMethodCallEnterTest extends AbstractEnterActionTestBase(DATA_PATH) {
  override protected def setSettings(): Unit = {
    super.setSettings()
    val settings = getCommonSettings
    settings.getIndentOptions.INDENT_SIZE = 2
    settings.ALIGN_MULTILINE_CHAINED_METHODS = true
  }
}

object ChainedMethodCallEnterTest {
  val DATA_PATH = "/actions/editor/enter/align_method_call_chain/"

  def suite = new ChainedMethodCallEnterTest
}