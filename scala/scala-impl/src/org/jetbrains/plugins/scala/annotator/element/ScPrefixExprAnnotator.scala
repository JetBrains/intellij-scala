package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr

object ScPrefixExprAnnotator extends ElementAnnotator[ScPrefixExpr] with DumbAware {

  override def annotate(element: ScPrefixExpr, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    for ((target, literal) <- ScNumericLiteralAnnotator.integerPrefixElement(element)) {
      ScNumericLiteralAnnotator.annotateIntOrLong(literal, target)
    }
  }
}
