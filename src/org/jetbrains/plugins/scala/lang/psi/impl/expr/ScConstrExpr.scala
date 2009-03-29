package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.expr.{ScConstrExpr}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScConstrExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstrExpr {
  override def toString: String = "ConstructorExpression"
}