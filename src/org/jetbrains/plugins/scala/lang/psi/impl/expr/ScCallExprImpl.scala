package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.toplevel.ScTypedDefinition
import types._
import nonvalue.{ScTypePolymorphicType, ScMethodType}
import api.expr._
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement}
import api.statements.{ScFunction, ScFun}
import resolve.ResolveUtils
import result.{Failure, TypeResult, Success, TypingContext}
import types.Compatibility.Expression

/**
 * @author ven
 */
trait ScCallExprImpl extends ScExpression {
  def operation: ScReferenceExpression

  def argumentExpressions: Seq[ScExpression]

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val bind = operation.bind
    bind match {
      case Some(r) => {
        val s = r.substitutor
        var tp = r.element match {
          case fun: ScFunction => s.subst(fun.polymorphicType)
          case fun: ScFun => s.subst(fun.polymorphicType)
          case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, s)
          case _ => Any
        }

        tp = tp match {
          case ScMethodType(ret, _, _) => ret
          case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
            val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
            ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
          }
          case _ => tp
        }
        Success(tp, Some(this))
      }
      case _ => Failure("Cannot resolve operation", Some(this))
    }
  }
}