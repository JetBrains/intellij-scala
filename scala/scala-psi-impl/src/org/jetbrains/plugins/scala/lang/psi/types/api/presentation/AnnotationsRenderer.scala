package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait AnnotationsRendererLike {

  def renderAnnotations(annotations: Seq[ScAnnotation]): String

  final def renderAnnotations(elem: ScAnnotationsHolder): String =
    renderAnnotations(elem.annotations)
}

class AnnotationsRenderer(
  typeRenderer: TypeRenderer,
  separator: String,
  escaper: TextEscaper = TextEscaper.Noop,
) extends AnnotationsRendererLike {

  def renderAnnotations(annotations: Seq[ScAnnotation]): String = {
    val annotationsRendered = annotations.iterator.map(renderAnnotation)
    val suffix = if (annotationsRendered.nonEmpty) separator else ""
    annotationsRendered.mkString("", separator, suffix)
  }

  protected def renderAnnotation(elem: ScAnnotation): String = {
    val buffer = new StringBuilder("@")

    val constrInvocation = elem.constructorInvocation
    val typ = constrInvocation.typeElement.`type`().getOrAny
    buffer.append(typeRenderer(typ))

    val arguments = elem.annotationExpr.getAnnotationParameters
    if (!shouldSkipArguments(typ, arguments))
      buffer.append(arguments.iterator.map(a => escaper.escape(a.getText)).mkString("(", ", ", ")"))
    buffer.result()
  }

  protected def shouldSkipArguments(annotationType: ScType, arguments: Seq[ScExpression]): Boolean =
    arguments.isEmpty
}
