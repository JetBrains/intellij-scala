package intellijhocon
package parser

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{StringEscapesTokenTypes, TokenType, PsiElement}
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import intellijhocon.lexer.HoconTokenType
import intellijhocon.Util
import com.intellij.lexer.StringLiteralLexer

class HoconErrorHighlightingAnnotator extends Annotator {

  import HoconTokenType._
  import HoconElementType._
  import HoconElementSets._
  import Util._

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element.getNode.getElementType match {
      case QuotedString =>
        val lexer = new StringLiteralLexer('"', QuotedString)
        lexer.start(element.getText)

        Iterator.continually {
          val range = TextRange(lexer.getTokenStart, lexer.getTokenEnd)
            .shiftRight(element.getTextRange.getStartOffset)
          val result = (lexer.getTokenType, range)
          lexer.advance()
          result
        }.takeWhile {
          case (tokenType, _) => tokenType != null
        } foreach {
          case (StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, range) =>
            holder.createErrorAnnotation(range, "invalid escape character")
          case (StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, range) =>
            holder.createErrorAnnotation(range, "invalid unicode escape")
          case _ =>
        }

      case Value =>
        def validateConcatenation(constrainingToken: IElementType, child: ASTNode): Unit = if (child != null) {
          (constrainingToken, child.getElementType) match {
            case (_, Substitution | BadCharacter | TokenType.ERROR_ELEMENT | TokenType.WHITE_SPACE) =>

              validateConcatenation(constrainingToken, child.getTreeNext)

            case (StringValue.extractor(), StringValue.extractor()) |
                 (Object, Object) | (Array, Array) | (null, _) =>

              validateConcatenation(child.getElementType, child.getTreeNext)

            case (required, actual) =>

              holder.createErrorAnnotation(child, s"cannot concatenate ${uncaps(required.toString)} with ${uncaps(actual.toString)}")
              validateConcatenation(actual, child.getTreeNext)

          }
        }

        validateConcatenation(null, element.getNode.getFirstChildNode)

      case _ =>

    }
  }
}
