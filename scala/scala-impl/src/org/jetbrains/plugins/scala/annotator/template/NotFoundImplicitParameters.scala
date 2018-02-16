package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitParametersOwner, InferUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.Seq

/**
  * Nikolay.Tropin
  * 16-Feb-18
  */
object NotFoundImplicitParameters extends AnnotatorPart[ImplicitParametersOwner] {

  override def annotate(element: ImplicitParametersOwner, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    element.findImplicitParameters.foreach {
      highlight(element, _, holder)
    }
  }

  def highlight(element: ImplicitParametersOwner, parameters: Seq[ScalaResolveResult], holder: AnnotationHolder): Unit = {
    parameters.filter(_.name == InferUtil.notFoundParameterName) match {
      case Seq() =>
      case params =>
        val types = params
          .map(_.implicitSearchState.map(_.tp.presentableText).getOrElse("unknown type"))

        holder.createErrorAnnotation(element, message(types))
    }
  }

  def message(types: Seq[String]): String =
    types.mkString("Implicit parameters not found for the following types: ", ", ", "")
}
