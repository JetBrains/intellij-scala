package org.jetbrains.plugins.scala
package findUsages
package apply

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

class ApplyMethodSearcher extends ApplyUnapplyMethodSearcherBase {

  protected val names: Set[String] = Set("apply")

  protected def checkAndTransform(ref: PsiReference): Option[ScReferenceElement] =
    (ref, ref.getElement.getContext) match {
    case (sref: ScReferenceExpression, x: ScMethodCall) if x.getInvokedExpr == ref.getElement => Some(sref)
    // TODO Check every ScMethodCall? Sounds expensive!
    case _ => None
  }

}

