package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.tree.TokenSet


/*final class ScalaDocMarkdownLexer extends MergingLexerAdapter(
  new ScalaDocAsteriskStripperLexer((new ScalaDocMarkdownFlavour).createInlinesLexer()),
  ScalaDocMarkdownLexer.TokensToMerge
)*/

final class ScalaDocMarkdownLexer extends MergingLexerAdapter(new _ScalaDocMarkdownLexer, ScalaDocMarkdownLexer.TokensToMerge)

object ScalaDocMarkdownLexer {
  private val TokensToMerge = TokenSet.EMPTY
}