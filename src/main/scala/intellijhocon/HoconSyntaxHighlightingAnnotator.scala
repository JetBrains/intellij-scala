package intellijhocon

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.openapi.util.TextRange

class HoconSyntaxHighlightingAnnotator extends Annotator {

  import HoconElementType._
  import HoconTokenType._

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element.getNode.getElementType match {
      case Null =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Null)
      case Boolean =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Boolean)
      case Number =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Number)
      case Include =>
        element.getNode.getChildren(TokenSet.create(UnquotedChars)).foreach {
          child => holder.createInfoAnnotation(child, null).setTextAttributes(HoconHighlighterColors.Include)
        }
      case Included =>
        element.getNode.getChildren(TokenSet.create(UnquotedChars)).foreach { child =>
          if (child.getChars.charAt(child.getTextLength - 1) == '(') {
            val (start, end) = (child.getTextRange.getStartOffset, child.getTextRange.getEndOffset)
            holder.createInfoAnnotation(new TextRange(start, end - 1), null).setTextAttributes(HoconHighlighterColors.IncludeModifier)
            holder.createInfoAnnotation(new TextRange(end - 1, end), null).setTextAttributes(HoconHighlighterColors.IncludeModifierParens)
          } else if (child.getTextLength == 1 && child.getChars.charAt(0) == ')') {
            holder.createInfoAnnotation(child, null).setTextAttributes(HoconHighlighterColors.IncludeModifierParens)
          }
        }
      case PathElement =>
        val pathParentType = element.getParent.getParent.getNode.getElementType
        element.getNode.getChildren(TokenSet.create(Period, UnquotedChars)).foreach { child =>
          val textAttributesKey = (child.getElementType, pathParentType) match {
            case (Period, _) => HoconHighlighterColors.PathSeparator
            case (UnquotedChars, Reference) => HoconHighlighterColors.ReferencePathElement
            case (UnquotedChars, _) => HoconHighlighterColors.PathElement
          }
          holder.createInfoAnnotation(child, null).setTextAttributes(textAttributesKey)
        }
      case _ =>
    }

  }
}
