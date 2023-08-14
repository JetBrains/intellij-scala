package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils.withStyledSpan
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAssignment
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{AnnotationsRenderer, TextEscaper, TypeRenderer}

final private [documentationProvider] class ScalaDocAnnotationRenderer(typeRenderer: TypeRenderer)
  extends AnnotationsRenderer(typeRenderer, "<br/>", TextEscaper.Html) {

  override def renderAnnotation(elem: ScAnnotation): String = {
    val arguments = elem.annotationExpr.getAnnotationParameters
    val constrInvocation = elem.constructorInvocation
    val typ = constrInvocation.typeElement.`type`().getOrAny
    val typeRendered = typeRenderer(typ)
    val argList =
      if (arguments.isEmpty)
        ""
      else
        arguments.map {
          case an: ScAnnotation =>
            renderAnnotation(an)
          case a: ScAssignment =>
            a.leftExpression.getText + a.rightExpression.fold("")(expr => " = " + HtmlPsiUtils.psiElement(expr))
          case expr =>
            HtmlPsiUtils.psiElement(expr)
        }.mkString("(", ", ", ")")
    withStyledSpan("@", DefaultHighlighter.ANNOTATION) + typeRendered + argList
  }
}
