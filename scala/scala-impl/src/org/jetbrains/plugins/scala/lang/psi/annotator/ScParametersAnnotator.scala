package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParametersImpl

trait ScParametersAnnotator extends Annotatable { self: ScParametersImpl =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    def checkRepeatedParams() {
      clauses.foreach { cl =>
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
