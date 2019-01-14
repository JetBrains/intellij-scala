package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScDo extends ScExpression {
  def condition: Option[ScExpression]

  /**
    * retrun loop expression of do statement
    *
    * @return body of do statement
    */
  def body: Option[ScExpression]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitDoStatement(this)
  }
}

object ScDo {
  def unapply(doStmt: ScDo): Option[(Option[ScExpression], Option[ScExpression])] =
    Some(doStmt.body, doStmt.condition)
}