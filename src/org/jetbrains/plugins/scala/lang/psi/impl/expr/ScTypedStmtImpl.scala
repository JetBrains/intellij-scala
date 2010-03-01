package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.expr._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import types.{ScType, Any}
import types.result.{Failure, TypeResult, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScTypedStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedStmt {
  override def toString: String = "TypedStatement"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = typeElement match {
    case Some(te) => te.getType(ctx)
    case None if !expr.isInstanceOf[ScUnderscoreSection] => expr.getType(ctx)
    case _ => Failure("Typed statement is not complete for underscore section", Some(this))
  }
}