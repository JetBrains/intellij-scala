package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}

class AnnotationsRenderer(
  typeRenderer: TypeRenderer,
  separator: String,
  escaper: TextEscaper = TextEscaper.Noop,
) {

  def renderAnnotations(elem: ScAnnotationsHolder): String = {
    val annotationsRendered = elem.annotations.iterator.map(renderAnnotation)
    val suffix = if (annotationsRendered.nonEmpty) separator else ""
    annotationsRendered.mkString("", separator, suffix)
  }

  private def renderAnnotation(elem: ScAnnotation): String = {
    val buffer = new StringBuilder("@")

    val constrInvocation = elem.constructorInvocation
    val typ = constrInvocation.typeElement.`type`().getOrAny
    buffer.append(typeRenderer(typ))

    val attrs = elem.annotationExpr.getAnnotationParameters
    if (attrs.nonEmpty) {
      buffer.append("(")
      var isFirst = true
      attrs.foreach { attr =>
        if (isFirst) isFirst = false
        else buffer.append(", ")
        buffer.append(escaper.escape(attr.getText))
      }
      buffer.append(")")
    }

    buffer.toString()
  }
}
