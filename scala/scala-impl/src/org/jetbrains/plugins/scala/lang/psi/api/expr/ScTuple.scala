package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScTuple extends ScInfixArgumentExpression {
  def exprs: Seq[ScExpression] = findChildren[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTuple(this)
  }

}

object ScTuple {
  def unapply(e: ScTuple): Some[Seq[ScExpression]] = Some(e.exprs)
}