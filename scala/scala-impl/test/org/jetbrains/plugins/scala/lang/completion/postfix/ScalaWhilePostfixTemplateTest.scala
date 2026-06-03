package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

import java.nio.file.Path

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`while` needs type inference to check conformance with Boolean")
class ScalaWhilePostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): Path = super.testPath() / "while"

  @Test
  def testSimple(): Unit = doTest()
}
