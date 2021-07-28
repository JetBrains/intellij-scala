package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScCharLiteral

object ScCharLiteralAnnotator extends ElementAnnotator[ScCharLiteral]  {
  override def annotate(element: ScCharLiteral, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    if (element.getValue == null) {
      holder.createErrorAnnotation(element, ScalaBundle.message("missing.char.value"))
    }
  }
}
