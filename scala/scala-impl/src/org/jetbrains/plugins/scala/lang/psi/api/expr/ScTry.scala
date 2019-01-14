package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
  * @author Alexander Podkhalyuzin
  */
trait ScTry extends ScExpression {
  def tryBlock: ScTryBlock = findChildByClassScala(classOf[ScTryBlock])

  def catchBlock: Option[ScCatchBlock] = findChild(classOf[ScCatchBlock])

  def finallyBlock: Option[ScFinallyBlock] = findChild(classOf[ScFinallyBlock])

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTryExpression(this)
  }
}

object ScTry {
  def unapply(tryStmt: ScTry): Option[(ScTryBlock, Option[ScCatchBlock], Option[ScFinallyBlock])] =
    Some((tryStmt.tryBlock, tryStmt.catchBlock, tryStmt.finallyBlock))
}