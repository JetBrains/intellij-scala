package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitParametersOwner, InferUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
object ImplicitParametersAnnotator extends AnnotatorPart[ImplicitParametersOwner] {

  override def kind: Class[ImplicitParametersOwner] = classOf[ImplicitParametersOwner]

  override def annotate(element: ImplicitParametersOwner, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    element.findImplicitParameters.foreach { params =>
      if (typeAware) highlightNotFound(element, params, holder)
    }
  }

  private def highlightNotFound(element: ImplicitParametersOwner, parameters: Seq[ScalaResolveResult], holder: AnnotationHolder): Unit = {
    parameters.filter(_.name == InferUtil.notFoundParameterName) match {
      case Seq() =>
      case params =>
        val types = params
          .map(_.implicitSearchState.map(_.tp.presentableText).getOrElse("unknown type"))

        val annotation = holder.createErrorAnnotation(element, message(types))
        adjustTextAttirbutesOf(annotation)
    }
  }

  private def adjustTextAttirbutesOf(annotation: Annotation) = {
    val errorStripeColor = annotation.getTextAttributes.getDefaultAttributes.getErrorStripeColor
    val attributes = new TextAttributes()
    attributes.setEffectType(null)
    attributes.setErrorStripeColor(errorStripeColor)
    annotation.setEnforcedTextAttributes(attributes)
  }

  def message(types: Seq[String]): String =
    types.mkString("No implicit arguments for: ", ", ", "")
}
