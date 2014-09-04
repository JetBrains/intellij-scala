package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
* @author ilyas, Alexander Podkhalyuzin
*/

class ScUnitExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnitExpr {
  override def toString: String = "UnitExpression"

  protected override def innerType(ctx: TypingContext) = Success(Unit, Some(this))
}