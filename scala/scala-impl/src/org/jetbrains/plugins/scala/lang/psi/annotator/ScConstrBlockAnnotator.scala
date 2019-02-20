package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.AuxiliaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock
import org.jetbrains.plugins.scala.lang.psi.api.{Annotatable, ScalaFile}

trait ScConstrBlockAnnotator extends Annotatable { self: ScConstrBlock =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    selfInvocation match {
      case Some(invocation) =>
        invocation.bind match {
          case AuxiliaryConstructor(constr) =>
            //check order
            if (constr.getTextRange.getStartOffset > getTextRange.getStartOffset) {
              holder.createErrorAnnotation(self, ScalaBundle.message("called.constructor.definition.must.precede"))
            }
          case _ =>
        }
      case None =>
        getContainingFile match {
          case file: ScalaFile if !file.isCompiled =>
            holder.createErrorAnnotation(this, ScalaBundle.message("constructor.invocation.expected"))
          case _ => //nothing to do in decompiled stuff
        }
    }
  }
}
