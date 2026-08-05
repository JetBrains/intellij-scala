package org.jetbrains.plugins.scala.lang.scaladoc.lexer.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.lexer.GeneratedLexer

trait CustomMarkdownLexer extends GeneratedLexer {
  override def getState: Int = this.yystate()

  def yystate(): Int
  def yybegin(newState: Int): Unit
  def getTokenStart: Int
  def getTokenEnd: Int


  override def advance(): IElementType = {
    yylex()
  }

  @throws[java.io.IOException]
  def yylex(): IElementType
}
