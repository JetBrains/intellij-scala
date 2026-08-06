package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters

object ScParametersAnnotator extends ElementAnnotator[ScParameters] {

  override def annotate(element: ScParameters, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    def checkRepeatedParams(): Unit = {
      element.clauses.foreach { cl =>
        cl.parameters.dropRight(1).foreach {
          case p if p.isRepeatedParameter =>
            val message = ScalaBundle.message("annotator.error.repeated.parameter.must.be.last")
            holder.createErrorAnnotation(p, message)
          case _ =>
        }
        cl.parameters.lastOption match {
          case Some(p) if p.isRepeatedParameter && cl.parameters.exists(_.isDefaultParam) =>
            holder.createErrorAnnotation(cl, ScalaBundle.message("annotator.error.repeated.or.default"))
          case _ =>
        }
      }
    }

    checkRepeatedParams()
  }
}
