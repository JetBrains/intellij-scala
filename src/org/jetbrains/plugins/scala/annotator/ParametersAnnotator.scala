package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}

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
    parameter.typeElement match {
      case None =>
        holder.createErrorAnnotation(parameter, "Missing type annotation for parameter: " + parameter.getName)
      case _ =>
    }
  }
}