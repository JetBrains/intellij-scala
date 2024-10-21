package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`while` needs type inference to check conformance with Boolean")
class ScalaWhilePostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "while/"

  def testSimple(): Unit = doTest()
}
