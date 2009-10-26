package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import types.{Bounds, ScType}
import types.result.{TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.Any

import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
*/

class ScTryStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTryStmt {
  override def toString: String = "TryStatement"


  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val lifted = tryBlock.getType(ctx)
    lifted flatMap { result => catchBlock match {
        case None => lifted
        case Some(cb) => {
          val branchTypes = cb.getBranches.map(_.getType(ctx))
          collectFailures(branchTypes, Any)(_.foldLeft(result)((t, b) => Bounds.lub(t, b)))
        }
      }
    }
  }
}