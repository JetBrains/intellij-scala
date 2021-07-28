package org.jetbrains.plugins.scala.annotator

class ScCharLiteralAnnotatorTest extends ScalaHighlightingTestBase {

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
