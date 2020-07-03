package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.extensions._

object ScParameterAnnotator extends ElementAnnotator[ScParameter] {

  override def annotate(element: ScParameter, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.owner match {
      case null =>
        val message = ScalaBundle.message("annotator.error.parameter.without.an.owner.name", element.name)
        holder.createErrorAnnotation(element, message)
      case _: ScMethodLike =>
        element.typeElement match {
          case None =>
            val message = ScalaBundle.message("annotator.error.missing.type.annotation.for.parameter", element.name)
            holder.createErrorAnnotation(element, message)
          case _ =>
        }
        if (element.isCallByNameParameter)
          annotateCallByNameParameter(element)
      case _: ScFunctionExpr =>
        element.typeElement match {
          case None =>
            element.expectedParamType match {
              case None =>
                val inFunctionLiteral = element.parents.drop(2).headOption.exists(_.is[ScFunctionExpr])
                if (!inFunctionLiteral) { // ScFunctionExprAnnotator does that more gracefully
                  holder.createErrorAnnotation(element, ScalaBundle.message("missing.parameter.type.name", element.name))
                }
              case _ =>
            }
          case _ =>
        }
    }
  }

  private def annotateCallByNameParameter(element: ScParameter)
                                         (implicit holder: ScalaAnnotationHolder): Any = {
    def errorWithMessageAbout(topic: String): Unit =
      holder.createErrorAnnotation(element, ScalaBundle.message("topic.parameters.may.not.be.call.by.name", topic))
    // TODO move to ScClassParameter
    element match {
      case cp: ScClassParameter if cp.isVal => errorWithMessageAbout("""'val'""")
      case cp: ScClassParameter if cp.isVar => errorWithMessageAbout("""'var'""")
      case cp: ScClassParameter if cp.isCaseClassVal => errorWithMessageAbout("case class")
      case p if p.isImplicitParameter && p.scalaLanguageLevel.forall(_ < ScalaLanguageLevel.Scala_2_13) =>
          errorWithMessageAbout("implicit")
      case _ =>
    }
  }
}
