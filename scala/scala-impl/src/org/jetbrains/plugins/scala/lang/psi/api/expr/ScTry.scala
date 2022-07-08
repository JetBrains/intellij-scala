package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScTry extends ScExpression {
  def expression: Option[ScExpression]

  def catchBlock: Option[ScCatchBlock]

  def finallyBlock: Option[ScFinallyBlock]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTry(this)
  }
}

object ScTry {
  def unapply(tryStmt: ScTry): Option[(Option[ScExpression], Option[ScCatchBlock], Option[ScFinallyBlock])] =
    Some((tryStmt.expression, tryStmt.catchBlock, tryStmt.finallyBlock))
}