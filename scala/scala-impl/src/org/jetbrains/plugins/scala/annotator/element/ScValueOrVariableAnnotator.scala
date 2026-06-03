package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

object ScValueOrVariableAnnotator extends ElementAnnotator[ScValueOrVariable] {

  override def annotate(element: ScValueOrVariable, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (!element.isInScala3Module) {
      element.annotationAscription.foreach { (ascription: ScAnnotations) =>
        holder.createWarningAnnotation(
          ascription,
          ScalaBundle.message("annotation.ascriptions.in.pattern.definitions.require.scala3"),
          ProblemHighlightType.GENERIC_ERROR
        )
      }
    }
}
