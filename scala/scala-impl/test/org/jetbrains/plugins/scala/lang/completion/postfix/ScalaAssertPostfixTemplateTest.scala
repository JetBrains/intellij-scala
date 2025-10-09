package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

import java.nio.file.Path

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`assert` needs type inference to check conformance with Boolean")
class ScalaAssertPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): Path = super.testPath() / "assert"

  @Test
  def testAssert(): Unit = doTest()

  @Test
  def testNotApplicable(): Unit = doNotApplicableTest()
}
