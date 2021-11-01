package org.jetbrains.plugins.scala.lang.imports.unused

import com.intellij.util.DocumentUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer

class OptimizeOnTheFlyTest extends ScalaLightCodeInsightFixtureTestAdapter {
  private def checkOptimizeOnTheFly(text: String, expected: String): Unit = {
    val file = myFixture.configureByText("dummy.scala", text)

    DocumentUtil.writeInRunUndoTransparentAction(new ScalaImportOptimizer(isOnTheFly = true).processFile(file))

    assertEqualsFailable(expected, myFixture.getEditor.getDocument.getText)
  }

  private def checkNotOptimizedOnTheFly(text: String): Unit = checkOptimizeOnTheFly(text, text)

  def testSimpleOptimize(): Unit = checkOptimizeOnTheFly(
    """import java.util.ArrayList
      |
      |object Foo""".stripMargin,
    """
      |
      |object Foo""".stripMargin
  )

  def testDontOptimizeWildcard(): Unit = checkNotOptimizedOnTheFly(
    """import java.util._
      |
      |object Foo""".stripMargin
  )

  def testDontOptimizeAlias(): Unit = checkNotOptimizedOnTheFly(
    """import java.util.{ArrayList => jList}
      |
      |object Foo""".stripMargin
  )

  def testDontOptimizeLocal(): Unit = checkNotOptimizedOnTheFly(
    """object Abc {
      |  def bar = 1
      |}
      |object Foo {
      |  import Abc.bar
      |
      |}""".stripMargin
  )

}
