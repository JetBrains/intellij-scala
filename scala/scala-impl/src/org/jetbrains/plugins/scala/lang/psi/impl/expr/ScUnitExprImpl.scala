package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnitExpr
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScUnitExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScUnitExpr {
  protected override def innerType: TypeResult = Right(Unit)

  override def toString: String = "UnitExpression"
}