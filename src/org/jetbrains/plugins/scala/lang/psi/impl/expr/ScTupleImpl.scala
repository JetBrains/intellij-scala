package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}

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