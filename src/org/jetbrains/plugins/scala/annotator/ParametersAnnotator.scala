package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr

/**
 * Pavel.Fatin, 15.06.2010
 */
trait ParametersAnnotator {
  def annotateParameters(parameters: ScParameters, holder: AnnotationHolder) {
    def repeatedParamMustComeLast {
      parameters.clauses.foreach {
        _.parameters.dropRight(1).filter(_.isRepeatedParameter).foreach {
          holder.createErrorAnnotation(_, "*-parameter must come last")
        }
      }
    }

    repeatedParamMustComeLast
  }
  
  def annotateParameter(parameter: ScParameter, holder: AnnotationHolder) {
    parameter.owner match {
      case null =>
        holder.createErrorAnnotation(parameter, "Parameter hasn't owner: " + parameter.getName)
      case method: ScMethodLike =>
        parameter.typeElement match {
          case None =>
            holder.createErrorAnnotation(parameter, "Missing type annotation for parameter: " + parameter.getName)
          case _ =>
        }
      case fun: ScFunctionExpr =>
        parameter.typeElement match {
          case None =>
            parameter.expectedParamType match {
              case None =>
                holder.createErrorAnnotation(parameter, "Missing parameter type: " + parameter.getName)
              case _ =>
            }
          case _ =>
        }
    }
  }
}