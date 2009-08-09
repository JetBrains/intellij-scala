package org.jetbrains.plugins.scala
package editor.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.{TokenSet, IElementType}
import lang.lexer.{ScalaLexer, ScalaTokenTypes}
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.09.2008
 */

class ScalaIndexPatternBuilder extends IndexPatternBuilder {
  def getIndexingLexer(file: PsiFile): Lexer = {
    file match {
      case _: ScalaFile => new ScalaLexer
      case _ => null
    }
  }
  def getCommentTokenSet(file: PsiFile): TokenSet = {
    file match {
      case _: ScalaFile => ScalaTokenTypes.COMMENTS_TOKEN_SET
      case _ => null
    }
  }
  def getCommentStartDelta(tokenType: IElementType) = 0
  def getCommentEndDelta(tokenType: IElementType) = 0
}