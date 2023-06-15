package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.template.PrivateBeanProperty
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

object ScAnnotationAnnotator extends ElementAnnotator[ScAnnotation] {

  override def annotate(element: ScAnnotation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    PrivateBeanProperty.annotate(element, typeAware)

    if (typeAware) {
      for {
        tpe <- element.typeElement.`type`().toOption
        cls <- tpe.extractClass
        if !cls.isAnnotationType
      } holder.createErrorAnnotation(element.annotationExpr, ScalaBundle.message("annotator.error.annotation.type.expected"))
    }
  }
}
