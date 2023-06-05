package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.HtmlPsiUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

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

final class ScalaDocAnnotationRenderer extends AnnotationsRenderer(null, "<br/>", TextEscaper.Html) {
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
      val link = HtmlPsiUtils.annotationLink(elem)
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
