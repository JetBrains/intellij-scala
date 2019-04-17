package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression

object ScTypedExpressionAnnotator extends ElementAnnotator[ScTypedExpression] {
  // TODO Shouldn't the ScExpressionAnnotator be enough?
  override def annotate(element: ScTypedExpression, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (typeAware) {
      for(it <- element.typeElement)
        checkConformance(element.expr, it, holder)
    }
  }
}
