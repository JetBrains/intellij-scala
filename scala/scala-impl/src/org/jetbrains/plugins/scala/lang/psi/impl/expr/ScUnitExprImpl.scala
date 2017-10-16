package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}

/**
* @author ilyas, Alexander Podkhalyuzin
*/

class ScUnitExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnitExpr {
  override def toString: String = "UnitExpression"

  protected override def innerType: TypeResult[ScType] = Success(Unit, Some(this))
}