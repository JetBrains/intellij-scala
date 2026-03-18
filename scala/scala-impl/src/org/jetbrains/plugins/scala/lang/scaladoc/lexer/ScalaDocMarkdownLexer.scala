package org.jetbrains.plugins.scala.lang.scaladoc.lexer

import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown.ScalaDocMarkdownFlavour


/*final class ScalaDocMarkdownLexer extends MergingLexerAdapter(
  new ScalaDocAsteriskStripperLexer((new ScalaDocMarkdownFlavour).createInlinesLexer()),
  ScalaDocMarkdownLexer.TokensToMerge
)*/

final class ScalaDocMarkdownLexer extends MergingLexerAdapter(new _ScalaDocMarkdownLexer, ScalaDocMarkdownLexer.TokensToMerge)

object ScalaDocMarkdownLexer {
  private val TokensToMerge = TokenSet.EMPTY
}