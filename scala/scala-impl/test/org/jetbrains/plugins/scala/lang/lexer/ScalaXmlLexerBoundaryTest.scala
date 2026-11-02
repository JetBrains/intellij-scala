package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert.{assertEquals, assertTrue}

class ScalaXmlLexerBoundaryTest extends LightJavaCodeInsightFixtureTestCase {
  def testOpeningBraceAtEndOfMalformedTag(): Unit = checkCoverage("(<i ( ){")

  def testOpeningBraceInsideMalformedTag(): Unit = checkCoverage("(<i ( ){value}")

  def testInjectionAfterXmlText(): Unit = checkCoverage("val x = <a>text{value}</a>")

  def testEscapedBracesInXmlText(): Unit = checkCoverage("val x = <a>text{{value}}</a>")

  private def checkCoverage(text: String): Unit = {
    for (isScala3 <- Seq(false, true)) {
      val lexer = new ScalaLexer(isScala3, getProject)
      lexer.start(text)
      var end = 0
      while (lexer.getTokenType != null) {
        assertEquals(s"Gap before ${lexer.getTokenType} (Scala 3: $isScala3)", end, lexer.getTokenStart)
        assertTrue("Lexer must advance", lexer.getTokenEnd > end)
        end = lexer.getTokenEnd
        lexer.advance()
      }
      assertEquals("Lexer must cover the whole source", text.length, end)
    }
  }
}
