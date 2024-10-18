package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.template.PrivateBeanProperty
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, isDumbMode}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

object ScAnnotationAnnotator extends ElementAnnotator[ScAnnotation] with DumbAware {

  override def annotate(element: ScAnnotation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    PrivateBeanProperty.annotate(element, typeAware)

    if (typeAware && !isDumbMode(element.getProject)) {
      for {
        tpe <- element.typeElement.`type`().toOption
        cls <- tpe.extractClass
        if !cls.isAnnotationType
      } holder.createErrorAnnotation(element.annotationExpr, ScalaBundle.message("annotator.error.annotation.type.expected"))
    }
  }
}
