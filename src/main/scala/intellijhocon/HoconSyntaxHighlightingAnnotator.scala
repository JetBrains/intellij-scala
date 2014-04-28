package intellijhocon

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.tree.TokenSet

class HoconSyntaxHighlightingAnnotator extends Annotator {

  import HoconElementType._
  import HoconTokenType._
  import DefaultLanguageHighlighterColors._

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element.getNode.getElementType match {
      case Null | Boolean =>
        holder.createInfoAnnotation(element, null).setTextAttributes(KEYWORD)
      case Number =>
        holder.createInfoAnnotation(element, null).setTextAttributes(NUMBER)
      case Include | Included =>
        element.getNode.getChildren(TokenSet.create(UnquotedChars)).foreach {
          child => holder.createInfoAnnotation(child, null).setTextAttributes(KEYWORD)
        }
      case _ =>
    }

  }
}
