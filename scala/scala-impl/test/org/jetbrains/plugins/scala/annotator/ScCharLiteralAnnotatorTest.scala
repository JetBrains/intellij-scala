package org.jetbrains.plugins.scala.annotator

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScCharLiteralAnnotatorTest extends ScalaHighlightingTestBase {
  import Message._

  def testEmptyCharLiteral(): Unit = {
    val scalaText =
      """
        |val test = ''
      """.stripMargin
    assertMatches(errorsFromScalaCode(scalaText)){
      case Error("''", "Missing char value") :: Nil =>
    }
  }
}
