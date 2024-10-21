package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`not` needs type inference to check conformance with Boolean")
class ScalaNotPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): String = super.testPath() + "not/"

  def testDoubleNot(): Unit = doTest()

  def testInMiddle(): Unit = doTest()

  def testParenthesized(): Unit = doTest()

  def testSimple(): Unit = doTest()

  def testSimplified(): Unit = doTest()

  def testUnknownType(): Unit = doTest()

  def testNotApplicable(): Unit = doNotApplicableTest()

  def testScl10247(): Unit = doTest()
}
