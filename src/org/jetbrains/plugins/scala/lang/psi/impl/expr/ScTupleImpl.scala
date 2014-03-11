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
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author ilyas, Alexander Podkhalyuzin
 */
class ScTupleImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTuple {
  override def toString: String = "Tuple"

  protected[expr] override def innerType(ctx: TypingContext): TypeResult[ScType] =
    if (exprs.length == 0) Success(Unit, Some(this))
    else {
      val tupleType = ScTupleType(exprs.map(_.getType(ctx).getOrAny))(getProject, getResolveScope)
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

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTupleExpr(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitTupleExpr(this)
      case _ => super.accept(visitor)
    }
  }
}