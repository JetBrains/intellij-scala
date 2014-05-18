package intellijhocon.parser

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement

class HoconErrorHighlightingAnnotator extends Annotator {

  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element.getNode.getElementType match {

      case _ =>

    }
  }
}
