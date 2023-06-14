package org.jetbrains.plugins.scala.editor.documentationProvider.renderers

import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.editor.documentationProvider.HtmlPsiUtils
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.{AnnotationsRenderer, TextEscaper}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt}

final private [documentationProvider] class ScalaDocAnnotationRenderer
  extends AnnotationsRenderer(null, "<br/>", TextEscaper.Html) {
  override def shouldSkipArguments(annotationType: ScType, arguments: Seq[ScExpression]): Boolean =
    arguments.isEmpty || isThrowsAnnotationConstructor(annotationType, arguments)

  // see SCL-17608
  private def isThrowsAnnotationConstructor(annotationType: ScType, arguments: Seq[ScExpression]): Boolean =
    if (arguments.size == 1) {
      //assuming that @throws annotation has single constructor with parametrized type which accepts java.lang.Class
      annotationType.extractClass.exists { clazz =>
        clazz.qualifiedName == "scala.throws" && arguments.head.`type`().exists(_.is[ScParameterizedType])
      }
    } else false

  override def renderAnnotation(elem: ScAnnotation): String = {
    val label = elem.annotationExpr.getText.takeWhile(_ != '(')
    val href = HtmlPsiUtils.psiElementHref(label)
    val escapedContent = StringEscapeUtils.escapeHtml("@" + label)
    val link =
      HtmlPsiUtils.withStyledSpan(
        s"""<a href="$href"><code>$escapedContent</code></a>""",
        DefaultHighlighter.ANNOTATION
      )
    val arguments = elem.annotationExpr.getAnnotationParameters
    val constrInvocation = elem.constructorInvocation
    val typ = constrInvocation.typeElement.`type`().getOrAny
    val argList =
      if (shouldSkipArguments(typ, arguments))
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
    link + argList
  }
}
