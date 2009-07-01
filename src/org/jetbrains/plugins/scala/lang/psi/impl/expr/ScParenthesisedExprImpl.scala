package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import api.expr._
import psi.ScalaPsiElementImpl
import types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
* Time: 9:24:19
*/

class ScParenthesisedExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParenthesisedExpr {
  override def toString: String = "ExpressionInParenthesis"

  override def getType = expr match {
    case Some(e) => e.cashedType
    case None => Nothing
  }
}