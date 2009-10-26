package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Unit
import types.result.{Success, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 */

class ScAssignStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAssignStmt {
  override def toString: String = "AssignStatement"

  protected override def innerType(ctx: TypingContext) = {
    getLExpression match {
      case call: ScMethodCall => call.getType(ctx)
      case _ => Success(Unit, Some(this))
    }
  }
}