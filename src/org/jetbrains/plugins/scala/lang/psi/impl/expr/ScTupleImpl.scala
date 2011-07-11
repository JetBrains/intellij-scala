package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types._
import result.{TypeResult, TypingContext, Success}
import java.lang.String
/**
 * @author ilyas, Alexander Podkhalyuzin
 */
class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTuple {
  override def toString: String = "Tuple"

  protected[expr] override def innerType(ctx: TypingContext): TypeResult[ScType] =
    if (exprs.length == 0) Success(Unit, Some(this))
    else {
      val tupleType = new ScTupleType(exprs.map(_.getType(ctx).getOrElse(Any)))(getProject, getResolveScope)
      Success(tupleType, Some(this))
    }

  def possibleApplications: Array[Array[(String, ScType)]] = {
    getContext match {
      case call: ScInfixExpr if isCall => {
        call.possibleApplications
      }
      case _ => Array.empty //todo: constructor
    }
  }
}