package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.project._

object ScLiteralTypeElementAnnotator extends ElementAnnotator[ScLiteralTypeElement] {

  override def annotate(element: ScLiteralTypeElement, typeAware: Boolean)
                       (implicit holder: AnnotationHolder): Unit = {
    if (!holder.getCurrentAnnotationSession.getFile.literalTypesEnabled) {
      holder.createErrorAnnotation(element, ScalaBundle.message("wrong.type.no.literal.types", element.getText))
    }
  }
}
