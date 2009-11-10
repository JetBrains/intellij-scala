package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types._
import result.{TypeResult, Failure, TypingContext, Success}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */
class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTuple {
  override def toString: String = "Tuple"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] =
    if (exprs.length == 0) Success(Unit, Some(this))
    else {
      Success(ScTupleType(exprs.map(_.getType(ctx).getOrElse(return Failure("Some components Failed to infer", Some(this))))), Some(this))
    }
}