package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.project._

object ScLiteralTypeElementAnnotator extends ElementAnnotator[ScLiteralTypeElement] {
  override def annotate(element: ScLiteralTypeElement, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    if (!holder.getCurrentAnnotationSession.getFile.literalTypesEnabled) {
      holder.createErrorAnnotation(element, ScalaBundle.message("wrong.type.no.literal.types", element.getText))
    }
  }
}
