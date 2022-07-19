package org.jetbrains.plugins.scala
package lang
package actions
package editor
package enter

import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}

class ChainedMethodCallEnterTest extends TestCase

object ChainedMethodCallEnterTest {
  val DATA_PATH = "/actions/editor/enter/align_method_call_chain/"

  def suite(): Test = new AbstractEnterActionTestBase(DATA_PATH) {
    override protected def setSettings(project: Project): Unit = {
      super.setSettings(project)
      val settings = getCommonSettings(project)
      settings.getIndentOptions.INDENT_SIZE = 2
      settings.ALIGN_MULTILINE_CHAINED_METHODS = true
    }
  }
}
