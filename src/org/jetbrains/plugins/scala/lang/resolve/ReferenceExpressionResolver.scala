package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.psi.impl.source.resolve.ResolveCache
import _root_.com.intellij.psi.{ResolveResult, PsiElement}
import psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import psi.types.Compatibility.Expression
import psi.types.Compatibility.Expression._

class ReferenceExpressionResolver(reference: ResolvableReferenceExpression) extends ResolveCache.PolyVariantResolver[ResolvableReferenceExpression] {
  case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)
  
  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr => {
        //TODO should rOp really be parsed as Tuple (not as argument list)?
        infixExpr.rOp match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  private def getContextInfo(ref: ResolvableReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic : ScGenericCall => getContextInfo(ref, generic)
      case call: ScMethodCall => ContextInfo(Some(call.argumentExpressions), () => None, false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType, true)
      case inf: ScInfixExpr if ref == inf.operation => {
        ContextInfo(if (ref.rightAssoc) Some(Seq(inf.lOp)) else inf.rOp match {
          case tuple: ScTuple => Some(tuple.exprs)
          case rOp => Some(Seq(rOp))
        }, () => None, false)
      }
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType, false)
    }
  }

  private def kinds(ref: ResolvableReferenceExpression, e: ScExpression, incomplete: Boolean): scala.collection.Set[ResolveTargets.Value] = {
    e.getContext match {
      case gen: ScGenericCall => kinds(ref, gen, incomplete)
      case parents: ScParenthesisedExpr => kinds(ref, parents, incomplete)
      case _: ScMethodCall | _: ScUnderscoreSection => StdKinds.methodRef
      case inf: ScInfixExpr if ref == inf.operation => StdKinds.methodRef
      case postf: ScPostfixExpr if ref == postf.operation => StdKinds.methodRef
      case pref: ScPrefixExpr if ref == pref.operation => StdKinds.methodRef
      case _ => reference.getKinds(incomplete)
    }
  }

  private def getTypeArgs(e : ScExpression) : Seq[ScTypeElement] = {
    e.getContext match {
      case generic: ScGenericCall => generic.arguments
      case parents: ScParenthesisedExpr => getTypeArgs(parents)
      case _ => Seq.empty
    }
  }

  def resolve(ref: ResolvableReferenceExpression, incomplete: Boolean): Array[ResolveResult] = {
    //TODO move this knowledge into MethodResolveProcessor
    val name = if(ref.isUnaryOperator) "unary_" + reference.refName else reference.refName

    val info = getContextInfo(ref, ref)

    val processor = new MethodResolveProcessor(ref, name, info.arguments.toList,
      getTypeArgs(ref), kinds(ref, ref, incomplete), info.expectedType.apply, info.isUnderscore)

    val result = reference.doResolve(ref, processor)

    if (result.isEmpty && ref.isAssignmentOperator) {
      reference.doResolve(ref, new MethodResolveProcessor(ref, reference.refName.init, List(argumentsOf(ref)), Nil))
    } else {
      result
    }
  }
}
