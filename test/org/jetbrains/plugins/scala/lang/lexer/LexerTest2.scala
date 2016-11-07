package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import org.jetbrains.plugins.scala.base.SimpleTestCase

/**
  * @author Nikolay.Tropin
  */
class LexerTest2  extends SimpleTestCase {
  protected var lexer: Lexer = new ScalaLexer()

  def testSCL6261(): Unit = {
    lexer.start("⚕")
    val tokenType = lexer.getTokenType
    assert(tokenType == ScalaTokenTypes.tIDENTIFIER, s"Token has wrong type: $tokenType")
  }
}
