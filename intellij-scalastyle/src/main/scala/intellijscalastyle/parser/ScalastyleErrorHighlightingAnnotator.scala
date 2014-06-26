package intellijscalastyle.parser

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.PsiElement

class ScalastyleErrorHighlightingAnnotator extends Annotator {
  println("Here")

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit = element.getNode.getElementType match {
    case x => println(">>> " + x)
  }
}
