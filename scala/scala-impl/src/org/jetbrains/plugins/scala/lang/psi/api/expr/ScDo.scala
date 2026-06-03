package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScDo extends ScExpression {
  def condition: Option[ScExpression]

  /**
    * retrun loop expression of do statement
    *
    * @return body of do statement
    */
  def body: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitDo(this)
  }
}

object ScDo {
  def unapply(doStmt: ScDo): Option[(Option[ScExpression], Option[ScExpression])] =
    Some(doStmt.body, doStmt.condition)
}