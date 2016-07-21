package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameters}

/**
 * Pavel.Fatin, 15.06.2010
 */
trait ParametersAnnotator {

  def annotateParameters(parameters: ScParameters, holder: AnnotationHolder): Unit = {
    def checkRepeatedParams() {
      parameters.clauses.foreach { cl =>
        cl.parameters.dropRight(1).foreach {
          case p if p.isRepeatedParameter => holder.createErrorAnnotation(p, "*-parameter must come last")
          case _ =>
        }
        cl.parameters.lastOption match {
          case Some(p) if p.isRepeatedParameter && cl.parameters.exists(_.isDefaultParam) =>
            holder.createErrorAnnotation(cl, "Parameter section with *-parameter cannot have default arguments")
          case _ =>
        }
      }
    }

    checkRepeatedParams()
  }

  def annotateParameter(parameter: ScParameter, holder: AnnotationHolder): Unit = {
    parameter.owner match {
      case null =>
        holder.createErrorAnnotation(parameter, "Parameter hasn't owner: " + parameter.name)
      case _: ScMethodLike =>
        parameter.typeElement match {
          case None =>
            holder.createErrorAnnotation(parameter, "Missing type annotation for parameter: " + parameter.name)
          case _ =>
        }
        if (parameter.isCallByNameParameter)
          annotateCallByNameParameter(parameter, holder: AnnotationHolder)
      case _: ScFunctionExpr =>
        parameter.typeElement match {
          case None =>
            parameter.expectedParamType match {
              case None =>
                holder.createErrorAnnotation(parameter, "Missing parameter type: " + parameter.name)
              case _ =>
            }
          case _ =>
        }
    }
  }

  private def annotateCallByNameParameter(parameter: ScParameter, holder: AnnotationHolder): Any = {
    def errorWithMessageAbout(topic: String) = {
      val message = s"$topic parameters may not be call-by-name"
      holder.createErrorAnnotation(parameter, message)
    }
    parameter match {
      case cp: ScClassParameter if cp.isVal => errorWithMessageAbout("\'val\'")
      case cp: ScClassParameter if cp.isVar => errorWithMessageAbout("\'var\'")
      case cp: ScClassParameter if cp.isCaseClassVal => errorWithMessageAbout("case class")
      case p if p.isImplicitParameter => errorWithMessageAbout("implicit")
      case _ =>
    }
  }
}
