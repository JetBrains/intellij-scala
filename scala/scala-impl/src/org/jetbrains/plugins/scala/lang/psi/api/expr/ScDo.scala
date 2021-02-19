package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScDoBase extends ScExpressionBase { this: ScDo =>
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

abstract class ScDoCompanion {
  def unapply(doStmt: ScDo): Option[(Option[ScExpression], Option[ScExpression])] =
    Some(doStmt.body, doStmt.condition)
}