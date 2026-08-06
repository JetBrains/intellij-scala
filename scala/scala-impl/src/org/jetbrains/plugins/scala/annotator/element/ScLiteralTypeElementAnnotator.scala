package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScNullLiteral, ScSymbolLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.project._

object ScLiteralTypeElementAnnotator extends ElementAnnotator[ScLiteralTypeElement] {

  override def annotate(element: ScLiteralTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!holder.getCurrentAnnotationSession.getFile.literalTypesEnabled) {
      holder.createErrorAnnotation(element, ScalaBundle.message("wrong.type.no.literal.types", element.getText))
    }
    else if (!element.isSingleton) {
      val literalName = element.getLiteral match {
        case _: ScInterpolatedStringLiteral => "string interpolator"
        case _: ScNullLiteral               => "'null'"
        case _: ScSymbolLiteral             => "quoted identifier"
        case other                          => other.toString.stripSuffix("Sc").stripSuffix("Literal") // not expected, but better be safe
      }
      holder.createErrorAnnotation(element, ScalaBundle.message("identifier.expected.but.0.found", literalName))
    }
  }
}
