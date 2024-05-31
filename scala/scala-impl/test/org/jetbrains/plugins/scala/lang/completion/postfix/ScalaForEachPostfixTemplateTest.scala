package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "`for` needs type inference to check sameOrInheritor")
class ScalaForEachPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath() = super.testPath() + "foreach/"

  def testExample(): Unit = doTest()
}
