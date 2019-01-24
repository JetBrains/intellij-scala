package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl

trait ScParameterAnnotator extends Annotatable { self: ScParameterImpl =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    owner match {
      case null =>
        holder.createErrorAnnotation(this, "Parameter hasn't owner: " + name)
      case _: ScMethodLike =>
        typeElement match {
          case None =>
            holder.createErrorAnnotation(this, "Missing type annotation for parameter: " + name)
          case _ =>
        }
        if (isCallByNameParameter)
          annotateCallByNameParameter(holder: AnnotationHolder)
      case _: ScFunctionExpr =>
        typeElement match {
          case None =>
            expectedParamType match {
              case None =>
                holder.createErrorAnnotation(this, "Missing parameter type: " + name)
              case _ =>
            }
          case _ =>
        }
    }
  }

  private def annotateCallByNameParameter(holder: AnnotationHolder): Any = {
    def errorWithMessageAbout(topic: String) = {
      val message = s"$topic parameters may not be call-by-name"
      holder.createErrorAnnotation(this, message)
    }
    // TODO move to ScClassParameter
    this match {
      case cp: ScClassParameter if cp.isVal => errorWithMessageAbout("\'val\'")
      case cp: ScClassParameter if cp.isVar => errorWithMessageAbout("\'var\'")
      case cp: ScClassParameter if cp.isCaseClassVal => errorWithMessageAbout("case class")
      case p if p.isImplicitParameter => errorWithMessageAbout("implicit")
      case _ =>
    }
  }
}
