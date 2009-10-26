package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr
import api.expr.{ScReferenceExpression, ScExpression}
import api.statements.ScFun
import api.toplevel.ScTypedDefinition
import com.intellij.psi.{PsiMethod, PsiNamedElement}
import types._
import result.{Success, TypingContext}

/**
 * @author ven
 */
trait ScCallExprImpl extends ScExpression {
  def operation : ScReferenceExpression

  protected override def innerType(ctx: TypingContext) =
    for {r <- wrap(operation.bind)} yield r.element match {
      case typed : ScTypedDefinition => r.substitutor.subst(typed.getType(TypingContext.empty) match {
        case Success(ScFunctionType(ret, _), _) => ret
        case t => t.getOrElse(Any)})
      case fun : ScFun => fun.retType
      case m : PsiMethod => r.substitutor.subst(ScType.create(m.getReturnType, m.getProject))
      case _ : PsiNamedElement => Any
  }
}