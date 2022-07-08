package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

class MethodRepr private (val itself: ScExpression,
                          val optionalBase: Option[ScExpression],
                          val args: Seq[ScExpression])

object MethodRepr {
  //method represented by optional base expression, optional method reference and arguments
  def unapply(expr: ScExpression): Option[(ScExpression, Option[ScExpression], Option[ScReferenceExpression], Seq[ScExpression])] =
    expr match {
      case null => None
      case expr => unapplyInner(expr)
    }

  //it is invoked very often in inspection, so BlockModificationTracker would be to heavy
  @CachedInUserData(expr, ModTracker.anyScalaPsiChange)
  private def unapplyInner(expr: ScExpression): Option[(ScExpression, Option[ScExpression], Option[ScReferenceExpression], Seq[ScExpression])] = {
    expr match {
      case call: ScMethodCall =>
        val args = call.args match {
          case exprList: ScArgumentExprList => exprList.exprs.map(stripped)
          case _ => Nil
        }
        call.getEffectiveInvokedExpr match {
          case baseExpr: ScExpression if call.isApplyOrUpdateCall && !call.isUpdateCall =>
            Some(expr, Some(baseExpr), None, args)
          case ref: ScReferenceExpression => Some(expr, ref.qualifier, Some(ref), args)
          case genericCall: ScGenericCall =>
            genericCall.referencedExpr match {
              case ref: ScReferenceExpression => Some(expr, ref.qualifier, Some(ref), args)
              case _ => Some(expr, None, None, args)
            }
          case methCall: ScMethodCall => Some(expr, Some(methCall), None, args)
          case _ => Some(expr, None, None, args)
        }
      case ScInfixExpr.withAssoc(base, operation, argument) =>
        val args = argument match {
          case tuple: ScTuple => tuple.exprs
          case _ => Seq(argument)
        }
        Some(expr, Some(stripped(base)), Some(operation), args)
      case prefix: ScPrefixExpr => Some(expr, Some(stripped(prefix.getBaseExpr)), Some(prefix.operation), Seq())
      case postfix: ScPostfixExpr => Some(expr, Some(stripped(postfix.getBaseExpr)), Some(postfix.operation), Seq())
      case refExpr: ScReferenceExpression =>
        refExpr.getParent match {
          case _: ScGenericCall => None
          case mc: ScMethodCall if !mc.isApplyOrUpdateCall => None
          case ScInfixExpr(_, `refExpr`, _) => None
          case ScPostfixExpr(_, `refExpr`) => None
          case ScPrefixExpr(`refExpr`, _) => None
          case _ => Some(expr, refExpr.qualifier, Some(refExpr), Seq())
        }
      case genCall: ScGenericCall =>
        genCall.getParent match {
          case mc: ScMethodCall if !mc.isApplyOrUpdateCall => None
          case _ => genCall.referencedExpr match {
            case ref: ScReferenceExpression => Some(genCall, ref.qualifier, Some(ref), Seq.empty)
            case _ => Some(genCall, None, None, Seq.empty)
          }
        }
      case _ => None
    }
  }

  def apply(itself: ScExpression, optionalBase: Option[ScExpression], args: Seq[ScExpression]): MethodRepr = {
    new MethodRepr(itself, optionalBase, args)
  }

}

object MethodSeq {
  def unapplySeq(expr: ScExpression): Option[Seq[MethodRepr]] = {
    val result = ArrayBuffer[MethodRepr]()
    @tailrec
    def extractMethods(expr: ScExpression): Unit = {
      expr match {
        case MethodRepr(_, optionalBase, _, args) =>
          result += MethodRepr(expr, optionalBase, args.toSeq)
          optionalBase match {
            case Some(ScParenthesisedExpr(inner)) => extractMethods(stripped(inner))
            case Some(expression) => extractMethods(expression)
            case _ =>
          }
        case _ =>
      }
    }
    extractMethods(expr)
    if (result.nonEmpty) Some(result.toSeq) else None
  }
}
