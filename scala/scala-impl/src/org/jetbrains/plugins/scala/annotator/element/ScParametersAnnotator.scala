package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters

object ScParametersAnnotator extends ElementAnnotator[ScParameters] {

  override def annotate(element: ScParameters, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    def checkRepeatedParams(): Unit = {
      element.clauses.foreach { cl =>
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
}
