package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Unit
import types.result.{TypingContext, Success}

/**
* @author ilyas, Alexander Podkhalyuzin
*/

class ScUnitExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnitExpr {
  override def toString: String = "UnitExpression"

  protected override def innerType(ctx: TypingContext) = Success(Unit, Some(this))
}