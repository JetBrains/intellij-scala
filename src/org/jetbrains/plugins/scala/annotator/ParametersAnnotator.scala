package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}

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
      case method: ScMethodLike =>
        parameter.typeElement match {
          case None =>
            holder.createErrorAnnotation(parameter, "Missing type annotation for parameter: " + parameter.name)
          case _ =>
        }
      case fun: ScFunctionExpr =>
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
}