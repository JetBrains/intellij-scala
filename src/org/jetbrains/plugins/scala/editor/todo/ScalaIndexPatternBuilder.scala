package org.jetbrains.plugins.scala
package editor.todo

import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

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

  def getCommentEndDelta(tokenType: IElementType): Int = tokenType match {
    case ScalaTokenTypes.tBLOCK_COMMENT => 2
    case ScalaDocElementTypes.SCALA_DOC_COMMENT => 2
    case _ => 0
  }
}