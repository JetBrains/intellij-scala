package org.jetbrains.plugins.scala.lang.lexer

class ScalaKeywordTokenType(val keywordText: String) extends ScalaTokenType(keywordText)

object ScalaKeywordTokenType {
  def apply(text: String) = new ScalaKeywordTokenType(text)
}