package org.jetbrains.plugins.scala.lang.psi.impl.expr
import api.expr.{ScReferenceExpression, ScExpression}
import api.statements.ScFun
import api.toplevel.ScTyped
import com.intellij.psi.{PsiMethod, PsiNamedElement}
import types._

/**
 * @author ven
 */
trait ScCallExprImpl extends ScExpression {
  def operation : ScReferenceExpression

  override def getType = operation.bind match {
    case None => Nothing
    case Some(r) if r.isCyclicReference => Nothing
    case Some(r) => r.element match {
      case typed : ScTyped => r.substitutor.subst(typed.calcType match {case ScFunctionType(ret, _) => ret case t => t})
      case fun : ScFun => fun.retType
      case m : PsiMethod => r.substitutor.subst(ScType.create(m.getReturnType, m.getProject))
      case _ : PsiNamedElement => Nothing
    }
  }
}