package intellijhocon.parser

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{TokenType, PsiElement}
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import intellijhocon.lexer.HoconTokenType
import intellijhocon.Util

class HoconErrorHighlightingAnnotator extends Annotator {

  import HoconTokenType._
  import HoconElementType._
  import HoconElementSets._
  import Util._

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element.getNode.getElementType match {
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
