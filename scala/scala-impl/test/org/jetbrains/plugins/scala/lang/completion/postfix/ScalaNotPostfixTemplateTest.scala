package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

import java.nio.file.Path

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`not` needs type inference to check conformance with Boolean")
class ScalaNotPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): Path = super.testPath() / "not"

  @Test
  def testDoubleNot(): Unit = doTest()

  @Test
  def testInMiddle(): Unit = doTest()

  @Test
  def testParenthesized(): Unit = doTest()

  @Test
  def testSimple(): Unit = doTest()

  @Test
  def testSimplified(): Unit = doTest()

  @Test
  def testUnknownType(): Unit = doTest()

  @Test
  def testNotApplicable(): Unit = doNotApplicableTest()

  @Test
  def testScl10247(): Unit = doTest()
}
