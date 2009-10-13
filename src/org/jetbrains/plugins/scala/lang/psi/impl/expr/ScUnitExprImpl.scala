package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Unit

/** 
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

class ScUnitExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnitExpr {
  override def toString: String = "UnitExpression"
  protected override def innerType = Unit
}