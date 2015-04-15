package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * @author Nikolay.Tropin
 */
class MethodRepr private (val itself: ScExpression,
                          val optionalBase: Option[ScExpression],
                          val optionalMethodRef: Option[ScReferenceExpression],
                          val args: Seq[ScExpression])

object MethodRepr {
  //method represented by optional base expression, optional method reference and arguments
  def unapply(expr: ScExpression): Option[(ScExpression, Option[ScExpression], Option[ScReferenceExpression], Seq[ScExpression])] = {
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
              case other => Some(expr, None, None, args)
            }
          case methCall: ScMethodCall => Some(expr, Some(methCall), None, args)
          case other => Some(expr, None, None, args)
        }
      case infix: ScInfixExpr =>
        val args = infix.getArgExpr match {
          case tuple: ScTuple => tuple.exprs
          case _ => Seq(infix.getArgExpr)
        }
        Some(expr, Some(stripped(infix.getBaseExpr)), Some(infix.operation), args)
      case prefix: ScPrefixExpr => Some(expr, Some(stripped(prefix.getBaseExpr)), Some(prefix.operation), Seq())
      case postfix: ScPostfixExpr => Some(expr, Some(stripped(postfix.getBaseExpr)), Some(postfix.operation), Seq())
      case refExpr: ScReferenceExpression =>
        refExpr.getParent match {
          case _: ScMethodCall | _: ScGenericCall => None
          case ScInfixExpr(_, `refExpr`, _) => None
          case ScPostfixExpr(_, `refExpr`) => None
          case ScPrefixExpr(`refExpr`, _) => None
          case _ => Some(expr, refExpr.qualifier, Some(refExpr), Seq())
        }
      case genCall: ScGenericCall =>
        genCall.getParent match {
          case _: ScMethodCall => None
          case _ => genCall.referencedExpr match {
            case ref: ScReferenceExpression => Some(genCall, ref.qualifier, Some(ref), Seq.empty)
            case other => Some(genCall, None, None, Seq.empty)
          }
        }
      case _ => None
    }
  }

  def apply(itself: ScExpression, optionalBase: Option[ScExpression], optionalMethodRef: Option[ScReferenceExpression], args: Seq[ScExpression]) = {
    new MethodRepr(itself, optionalBase, optionalMethodRef, args)
  }

}

object MethodSeq {
  def unapplySeq(expr: ScExpression): Option[Seq[MethodRepr]] = {
    val result = ArrayBuffer[MethodRepr]()
    @tailrec
    def extractMethods(expr: ScExpression) {
      expr match {
        case MethodRepr(itself, optionalBase, optionalMethodRef, args) =>
          result += MethodRepr(expr, optionalBase, optionalMethodRef, args)
          optionalBase match {
            case Some(ScParenthesisedExpr(inner)) => extractMethods(stripped(inner))
            case Some(expression) => extractMethods(expression)
            case _ =>
          }
        case _ =>
      }
    }
    extractMethods(expr)
    if (result.length > 0) Some(result) else None
  }
}