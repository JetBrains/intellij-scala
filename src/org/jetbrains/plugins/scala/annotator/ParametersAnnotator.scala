package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import com.intellij.lang.annotation.AnnotationHolder

/**
 * Pavel.Fatin, 15.06.2010
 */

trait ParametersAnnotator {
  def annotateParameters(parameters: ScParameters, holder: AnnotationHolder) {
    parameters.clauses.foreach {
      _.parameters.dropRight(1).filter(_.isRepeatedParameter).foreach {
        holder.createErrorAnnotation(_, "*-parameter must come last")
      }
    }
  }
}