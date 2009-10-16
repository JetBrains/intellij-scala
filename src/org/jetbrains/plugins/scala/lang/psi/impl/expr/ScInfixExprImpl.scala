package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScInfixExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixExpr with ScCallExprImpl {
  override def toString: String = "InfixExpression"
}