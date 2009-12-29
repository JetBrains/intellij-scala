package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Nothing
import types.result.{Success, TypingContext}

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScThrowStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThrowStmt {
  override def toString: String = "ThrowStatement"

  protected override def innerType(ctx: TypingContext) = Success(Nothing, Some(this))

  def body = findChild(classOf[ScExpression])
}