package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.statements.ScFun
import api.toplevel.ScTyped
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._
import types.{ScType, Nothing, ScFunctionType}

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr with ScCallExprImpl {
  override def toString: String = "InfixExpression"
}