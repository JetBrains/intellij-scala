package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

import java.nio.file.Path

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`notnull` needs type inference to check conformance with AnyRef")
class ScalaNotNullPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): Path = super.testPath() / "notnull"

  @Test
  def testChain(): Unit = doTest()

  @Test
  def testInfix(): Unit = doTest()

  @Test
  def testMethodCall(): Unit = doTest()

  @Test
  def testNotApplicableBoolean(): Unit = doNotApplicableTest()

  @Test
  def testNotApplicableInt(): Unit = doNotApplicableTest()

  @Test
  def testParenthesized(): Unit = doTest()

  @Test
  def testSimple(): Unit = doTest()
}
