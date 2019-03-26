package org.jetbrains.plugins.scala.lang.actions.editor.enter

import org.jetbrains.plugins.scala.lang.actions.editor.enter.ChainedMethodCallEnterTest.DATA_PATH
import org.jetbrains.plugins.scala.util.{AllTestsScala, TestUtils}
import org.junit.runner.RunWith

@RunWith(classOf[AllTestsScala])
object ChainedMethodCallEnterTest {
  val DATA_PATH = "/actions/editor/enter/data/indents/align_method_call_chain/"

  def suite = new ChainedMethodCallEnterTest
}

class ChainedMethodCallEnterTest extends AbstractEnterActionTestBase(TestUtils.getTestDataPath + DATA_PATH) {
  override protected def setSettings(): Unit = {
    super.setSettings()
    val settings = getCommonSettings
    settings.getIndentOptions.INDENT_SIZE = 2
    settings.ALIGN_MULTILINE_CHAINED_METHODS = true
    myEditor
  }
}