package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`for` needs type inference to check sameOrInheritor")
class ScalaForEachPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath() = super.testPath() + "foreach/"

  def testExample(): Unit = doTest()
}
