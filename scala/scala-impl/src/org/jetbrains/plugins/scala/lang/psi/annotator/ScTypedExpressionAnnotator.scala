package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.checkConformance
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedExpression


trait ScTypedExpressionAnnotator extends Annotatable { self: ScTypedExpression =>

  // TODO Shouldn't the ScExpressionAnnotator be enough?
  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    if (typeAware) {
      for(element <- typeElement)
        checkConformance(expr, element, holder)
    }
  }
}
