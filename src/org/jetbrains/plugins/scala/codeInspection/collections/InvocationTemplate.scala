package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Nikolay.Tropin
 */

class InvocationTemplate(nameCondition: String => Boolean) {

  private class Condition[T](f: T => Boolean) {
    def and(other: T => Boolean): Condition[T] = new Condition[T](x => f(x) && other(x))

    def apply(t: T) = f(t)
  }

  private var refCondition = new Condition[ScReferenceExpression](_ => true)

  def from(patterns: Array[String]): this.type = {
    refCondition = refCondition.and(checkResolve(_, patterns))
    this
  }
  
  def ref(otherRefCondition: ScReferenceExpression => Boolean): this.type = {
    refCondition = refCondition.and(otherRefCondition)
    this
  }

  def unapplySeq(expr: ScExpression): Option[(ScExpression, Seq[ScExpression])] = {
    stripped(expr) match {
      case (mc: ScMethodCall) childOf (parentCall: ScMethodCall) if !parentCall.isApplyOrUpdateCall => None
      case MethodRepr(_, qualOpt, Some(ref), args) if nameCondition(ref.refName) && refCondition(ref) =>
        Some(qualOpt.orNull, args)
      case MethodRepr(call: ScMethodCall, Some(qual), None, args) if nameCondition("apply") && call.isApplyOrUpdateCall && !call.isUpdateCall =>
        val text = qual match {
          case _: ScReferenceExpression | _: ScMethodCall | _: ScGenericCall => s"${qual.getText}.apply"
          case _ => s"(${qual.getText}).apply"
        }
        val ref = ScalaPsiElementFactory.createExpressionFromText(text, call).asInstanceOf[ScReferenceExpression]
        if (refCondition(ref)) Some(qual, args)
        else None
      case MethodRepr(_, Some(MethodRepr(_, qualOpt, Some(ref), firstArgs)), None, secondArgs) if nameCondition(ref.refName) && refCondition(ref) => 
        Some(qualOpt.orNull, firstArgs ++ secondArgs)
      case _ => None
    }
  }
}
