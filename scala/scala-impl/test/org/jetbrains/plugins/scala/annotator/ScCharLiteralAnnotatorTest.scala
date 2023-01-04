package org.jetbrains.plugins.scala.annotator

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
