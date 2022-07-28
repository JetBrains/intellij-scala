package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.util.Try

abstract class InvocationTemplate(nameCondition: String => Boolean) {

  protected class Condition[T](f: T => Boolean) {
    def and(other: T => Boolean): Condition[T] = new Condition[T](x => f(x) && other(x))

    def apply(t: T): Boolean = f(t)
  }

  protected var refCondition = new Condition[ScReferenceExpression](_ => true)

  def from(patterns: Seq[String]): this.type = {
    refCondition = refCondition.and(checkResolve(_, patterns))
    this
  }

  def ref(otherRefCondition: ScReferenceExpression => Boolean): this.type = {
    refCondition = refCondition.and(otherRefCondition)
    this
  }
}

class Qualified(nameCondition: String => Boolean) extends InvocationTemplate(nameCondition) {
  def unapplySeq(expr: ScExpression): Option[(ScExpression, Seq[ScExpression])] = {
    stripped(expr) match {
      case (_: ScMethodCall) childOf (parentCall: ScMethodCall) if !parentCall.isApplyOrUpdateCall => None
      case MethodRepr(_, Some(qual), Some(ref), args) if nameCondition(ref.refName) && refCondition(ref) =>
        Some((qual, args))
      case MethodRepr(call: ScMethodCall, Some(qual), None, args) if nameCondition("apply") && call.isApplyOrUpdateCall && !call.isUpdateCall =>
        val text = qual match {
          case _: ScReferenceExpression | _: ScMethodCall | _: ScGenericCall => s"${qual.getText}.apply"
          case _ => s"(${qual.getText}).apply"
        }
        val ref = Try(ScalaPsiElementFactory.createExpressionFromText(text, call).asInstanceOf[ScReferenceExpression]).toOption
        if (ref.isDefined && refCondition(ref.get)) Some(qual, args)
        else None
      case MethodRepr(_, Some(MethodRepr(_, Some(qual), Some(ref), firstArgs)), None, secondArgs) if nameCondition(ref.refName) && refCondition(ref) =>
        Some(qual, (firstArgs ++ secondArgs))
      case _ => None
    }
  }
}

class Unqualified(nameCondition: String => Boolean) extends InvocationTemplate(nameCondition) {
  def unapplySeq(expr: ScExpression): Option[Seq[ScExpression]] = {
    stripped(expr) match {
      case (_: ScMethodCall) childOf (_: ScMethodCall) => None
      case MethodRepr(_, None, Some(ref), args) if nameCondition(ref.refName) && refCondition(ref) =>
        Some(args)
      case MethodRepr(_, Some(MethodRepr(_, None, Some(ref), firstArgs)), None, secondArgs) if nameCondition(ref.refName) && refCondition(ref) =>
        Some(firstArgs ++ secondArgs)
      case _ => None
    }
  }
}
