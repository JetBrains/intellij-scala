package org.jetbrains.plugins.scala.editor.todo

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.IndexPatternBuilder
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes.SCALA_DOC_COMMENT

//noinspection TypeAnnotation
final class ScalaIndexPatternBuilder extends IndexPatternBuilder {

  import ScalaTokenTypes._

  override def getIndexingLexer(file: PsiFile) =
    byParserDefinition[Lexer](file)(_.createLexer(null))

  override def getCommentTokenSet(file: PsiFile) =
    byParserDefinition[TokenSet](file)(_.getCommentTokens)

  override def getCommentStartDelta(tokenType: IElementType) = tokenType match {
    case `tLINE_COMMENT` |
         `tBLOCK_COMMENT` => 2
    case `tDOC_COMMENT` |
         `SCALA_DOC_COMMENT` => 3
    case _ => 0
  }

  override def getCommentEndDelta(tokenType: IElementType) = tokenType match {
    case `tBLOCK_COMMENT` |
         `SCALA_DOC_COMMENT` => 2
    case _ => 0
  }

  override def getCharsAllowedInContinuationPrefix(tokenType: IElementType) = tokenType match {
    case `tBLOCK_COMMENT` |
         `tDOC_COMMENT` |
         `SCALA_DOC_COMMENT` => "*"
    case _ => ""
  }

  private def byParserDefinition[T >: Null](file: PsiFile)
                                           (function: ParserDefinition => T) =
    file match {
      case file: ScalaFile with PsiFileBase => function(file.getParserDefinition)
      case _ => null
    }
}