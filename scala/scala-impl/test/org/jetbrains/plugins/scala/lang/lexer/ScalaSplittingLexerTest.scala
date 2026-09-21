package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.{FlexAdapter, MergingLexerAdapter}
import com.intellij.psi.tree.TokenSet
import junit.framework.TestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx.SCALA_PLAIN_CONTENT
import org.jetbrains.plugins.scala.lang.lexer.core._ScalaSplittingLexer
import org.junit.Assert._

class ScalaSplittingLexerTest extends TestCase {
  private def checkInterpolation(text: String): Unit = {
    val lexer = new MergingLexerAdapter(
      new FlexAdapter(new _ScalaSplittingLexer(null)),
      TokenSet.create(SCALA_PLAIN_CONTENT, tBLOCK_COMMENT)
    )
    // The core lexer must receive the complete interpolation, including comments in injections.
    // A real comment following the string must still be split off.
    lexer.start(text + "/* after */")
    assertEquals(SCALA_PLAIN_CONTENT, lexer.getTokenType)
    assertEquals(text, lexer.getTokenText)
    lexer.advance()
    assertEquals(tBLOCK_COMMENT, lexer.getTokenType)
    assertEquals("/* after */", lexer.getTokenText)
    lexer.advance()
    assertNull(lexer.getTokenType)

    lexer.start("/* reset */")
    assertEquals(tBLOCK_COMMENT, lexer.getTokenType)
    assertEquals("/* reset */", lexer.getTokenText)
  }

  def testStringInInjection(): Unit =
    checkInterpolation("""s"${getResources("classpath:env/*").mkString(", ")}"""")

  def testNestedInterpolation(): Unit =
    checkInterpolation("""s"ccc s${s"/*"} ddd"""")

  def testMultilineInterpolation(): Unit =
    checkInterpolation("s\"\"\"${s\"\"\"/*\"\"\"} //\"\"\"")

  def testBracesInInjection(): Unit =
    checkInterpolation("""s"${ { val x = "}"; "/*" } } //"""")

  def testCommentsInInjection(): Unit =
    checkInterpolation("""s"${ /* } " /* nested */ */ "/**/" } //"""")

  def testLineCommentInInjection(): Unit =
    checkInterpolation("s\"${ // } \"\n\"/*\" } //\"")

  def testCommentsInMultilineInjection(): Unit = {
    // SCL-25973: comments must not split an ordinary interpolated string at a newline.
    Seq("// comment\n    x", "/* comment */\n    x", "x // comment").foreach { injection =>
      checkInterpolation("s\"a${\n    " + injection + "\n}\"")
    }
  }

  def testEscapedDollar(): Unit =
    checkInterpolation("""s"$${ /* """")

  def testEscapedQuote(): Unit =
    checkInterpolation("""s"\" /* ${"//"}"""")

  def testRawInterpolation(): Unit =
    checkInterpolation("""raw"\${"/*"}"""")

  def testRawClosingQuote(): Unit =
    checkInterpolation("""raw"\"""")

  def testBackquotedInjection(): Unit =
    checkInterpolation("""s"$`"${/*` //"""")
}
