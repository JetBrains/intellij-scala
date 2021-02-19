package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
  * @author Alexander Podkhalyuzin
  */
trait ScTryBase extends ScExpressionBase { this: ScTry =>
  def expression: Option[ScExpression]

  def catchBlock: Option[ScCatchBlock]

  def finallyBlock: Option[ScFinallyBlock]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTry(this)
  }
}

abstract class ScTryCompanion {
  def unapply(tryStmt: ScTry): Option[(Option[ScExpression], Option[ScCatchBlock], Option[ScFinallyBlock])] =
    Some((tryStmt.expression, tryStmt.catchBlock, tryStmt.finallyBlock))
}