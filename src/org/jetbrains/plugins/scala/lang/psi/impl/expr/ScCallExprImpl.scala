package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
import api.statements.ScFun
import api.toplevel.ScTypedDefinition
import types._
import result.{TypeResult, Success, TypingContext}
import api.expr._
import com.intellij.psi.{PsiElement, PsiMethod, PsiNamedElement}

/**
 * @author ven
 */
trait ScCallExprImpl extends ScExpression {
  def operation : ScReferenceExpression

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    def isOneMoreCall(elem: PsiElement): Boolean = {
      elem.getParent match {
        case _: ScMethodCall => true
        case _: ScUnderscoreSection => true
        case _: ScParenthesisedExpr => isOneMoreCall(elem.getParent)
        case _ => false
      }
    }
    for{r <- wrap(operation.bind)} yield r.element match {
      case typed: ScTypedDefinition => r.substitutor.subst(typed.getType(TypingContext.empty) match {
        case Success(ScFunctionType(ret, _), _) => {
          ret match {
            case fun: ScFunctionType if fun.isImplicit && !isOneMoreCall(this) => fun.returnType
            case _ => ret
          }
        }
        case t => t.getOrElse(return t)
      })
      case fun: ScFun => fun.retType
      case m: PsiMethod => r.substitutor.subst(ScType.create(m.getReturnType, m.getProject))
      case _: PsiNamedElement => Any
    }
  }
}