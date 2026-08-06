package org.jetbrains.plugins.scala.lang.lexer;

abstract class ScalaLexerTestBase extends LexerTestBase {
  override protected def printTokenRange(tokenStart: Int, tokenEnd: Int, builder: StringBuilder): Unit = {}
}
