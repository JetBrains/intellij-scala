package org.jetbrains.plugins.hocon.highlight

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.hocon.HoconConstants

class HoconSyntaxHighlightingAnnotator extends Annotator {

  import org.jetbrains.plugins.hocon.CommonUtil._
  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementSets._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    lazy val parentType = element.getParent.getNode.getElementType
    lazy val firstChildType = element.getFirstChild.getNode.getElementType
    element.getNode.getElementType match {
      case Null =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Null)

      case Boolean =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Boolean)

      case Number =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Number)

      case UnquotedChars if parentType == Include =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.Include)

      case UnquotedChars if parentType == Included =>
        if (HoconConstants.IncludeQualifiers.contains(element.getText)) {
          val TextRange(start, end) = element.getTextRange
          holder.createInfoAnnotation(TextRange(start, end - 1), null).setTextAttributes(HoconHighlighterColors.IncludeModifier)
          holder.createInfoAnnotation(TextRange(end - 1, end), null).setTextAttributes(HoconHighlighterColors.IncludeModifierParens)
        } else if (element.getText == ")") {
          holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.IncludeModifierParens)
        }

      case KeyPart if firstChildType == UnquotedString =>
        val textAttributesKey = element.getParent.getParent.getNode.getElementType match {
          case Path => HoconHighlighterColors.SubstitutionKey
          case KeyedField.extractor() => HoconHighlighterColors.EntryKey
        }
        holder.createInfoAnnotation(element, null).setTextAttributes(textAttributesKey)

      case Period if parentType == Path || parentType == PrefixedField =>
        holder.createInfoAnnotation(element, null).setTextAttributes(HoconHighlighterColors.PathSeparator)

      case _ =>
    }

  }
}
