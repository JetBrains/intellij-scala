package org.jetbrains.plugins.scala
package findUsages
package apply

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{&&, Parent}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScMethodCall, ScReferenceExpression}

class ApplyMethodSearcher extends ApplyUnapplyMethodSearcherBase {

  override protected val names: Set[String] = Set("apply")

  override protected def checkAndTransform(ref: PsiReference): Option[ScReference] =
    (ref, ref.getElement.getContext) match {
      case (sref: ScReferenceExpression, methodCall: ScMethodCall) if methodCall.getInvokedExpr == ref.getElement => Some(sref)
      case (sref: ScReferenceExpression, (genericCall: ScGenericCall) && Parent(methodCall: ScMethodCall))
        if genericCall.referencedExpr == ref.getElement && methodCall.getInvokedExpr == genericCall => Some(sref)
      // TODO Check every ScMethodCall? Sounds expensive!
      case _ => None
    }

}

