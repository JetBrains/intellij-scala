package org.jetbrains.plugins.scala.lang.completion.postfix

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.junit.Test

import java.nio.file.Path

@WithIndexingMode(mode = IndexingMode.SMART, reason = "`for` needs type inference to check sameOrInheritor")
class ScalaForEachPostfixTemplateTest extends PostfixTemplateTest {
  override def testPath(): Path = super.testPath() / "foreach"

  @Test
  def testExample(): Unit = doTest()
}
