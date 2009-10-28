package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.expr._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import types.result.{TypeResult, TypingContext}
import types.{ScType, Any}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScTypedStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedStmt {
  override def toString: String = "TypedStatement"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = typeElement match {
    case Some(te) => te.getType(ctx)
    case None => expr.getType(ctx)
  }
}