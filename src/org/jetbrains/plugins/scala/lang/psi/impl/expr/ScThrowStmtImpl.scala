package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Nothing
import types.result.{Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScThrowStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScThrowStmt {
  override def toString: String = "ThrowStatement"

  protected override def innerType(ctx: TypingContext) =
    Failure("Cannot infer type of `throw' expression", Some(this))
}