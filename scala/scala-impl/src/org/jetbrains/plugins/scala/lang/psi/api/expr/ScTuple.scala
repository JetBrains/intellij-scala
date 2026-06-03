package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScTuple extends ScInfixArgumentExpression {
  def exprs: Seq[ScExpression] = findChildren[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTuple(this)
  }

}

object ScTuple {
  def unapply(e: ScTuple): Some[Seq[ScExpression]] = Some(e.exprs)
}