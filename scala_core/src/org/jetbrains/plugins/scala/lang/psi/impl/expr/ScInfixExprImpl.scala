package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.statements.ScFun
import types.{ScType, Nothing}
import api.toplevel.ScTyped
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr {
  override def toString: String = "InfixExpression"

  override def getType = operation.bind match {
    case None => Nothing
    case Some(r) => r.element match {
      case typed : ScTyped => r.substitutor.subst(typed.calcType)
      case fun : ScFun => fun.retType
      case m : PsiMethod => r.substitutor.subst(ScType.create(m.getReturnType, getProject))
    }
  }
}