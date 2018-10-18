package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrExpr

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
final class ScConstrExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrExpr {

  override def toString: String = "ConstructorExpression"
}