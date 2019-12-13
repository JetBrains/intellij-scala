package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.AuxiliaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

object ScConstrBlockAnnotator extends ElementAnnotator[ScConstrBlock] {

  override def annotate(element: ScConstrBlock, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    element.selfInvocation match {
      case Some(invocation) =>
        invocation.bind match {
          case AuxiliaryConstructor(constr) =>
            //check order
            if (constr.getTextRange.getStartOffset > element.getTextRange.getStartOffset) {
              holder.createErrorAnnotation(element, ScalaBundle.message("called.constructor.definition.must.precede"))
            }
          case _ =>
        }
      case None =>
        element.getContainingFile match {
          case file: ScalaFile if !file.isCompiled =>
            holder.createErrorAnnotation(element, ScalaBundle.message("constructor.invocation.expected"))
          case _ => //nothing to do in decompiled stuff
        }
    }
  }
}
