package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
import api.toplevel.ScTypedDefinition
import types._
import nonvalue.{ScTypePolymorphicType, ScMethodType}
import result.{TypeResult, Success, TypingContext}
import api.expr._
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement}
import api.statements.{ScFunction, ScFun}
import resolve.ResolveUtils

/**
 * @author ven
 */
trait ScCallExprImpl extends ScExpression {
  def operation : ScReferenceExpression

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    for{r <- wrap(operation.bind)} yield {
      val s = r.substitutor
      val tp = r.element match {
        case fun: ScFunction => s.subst(fun.polymorphicType)
        case fun: ScFun => s.subst(fun.polymorphicType)
        case m: PsiMethod => ResolveUtils.javaPolymorphicType(m, s)
        case _ => Any
      }

      tp match {
        case ScMethodType(ret, _, _) => ret
        case ScTypePolymorphicType(ScMethodType(ret, _, _), params) => {
          //todo: local type inference
          ScTypePolymorphicType(ret, params)
        }
        case _ => tp
      }
    }
  }
}