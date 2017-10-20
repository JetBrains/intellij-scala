package org.jetbrains.plugins.hocon.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.lexer.StringLiteralLexer
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, StringEscapesTokenTypes, TokenType}

import scala.annotation.tailrec

class HoconErrorHighlightingAnnotator extends Annotator {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

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

      case Concatenation =>
        @tailrec
        def validateConcatenation(constrainingToken: IElementType, child: ASTNode): Unit = if (child != null) {
          (constrainingToken, child.getElementType) match {
            case (_, Substitution | BadCharacter | TokenType.ERROR_ELEMENT | TokenType.WHITE_SPACE) =>

              validateConcatenation(constrainingToken, child.getTreeNext)

            case (StringValue, StringValue) |
                 (Object, Object) |
                 (Array, Array) |
                 (null, _) =>

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
