package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrExpr

object ScConstrExprAnnotator extends ElementAnnotator[ScConstrExpr] {

  override def annotate(constrExpr: ScConstrExpr, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (constrExpr.selfInvocation.isEmpty) {
      constrExpr.getContainingFile match {
        case file: ScalaFile if !file.isCompiled =>
          holder.createErrorAnnotation(constrExpr, ScalaBundle.message("constructor.invocation.expected"))
        case _ => //nothing to do in decompiled stuff
      }
    }
  }
}
