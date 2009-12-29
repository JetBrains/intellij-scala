package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/**
* @author Alexander Podkhalyuzin
*/

trait ScThrowStmt extends ScExpression {
  override def accept(visitor: ScalaElementVisitor) = visitor.visitThrowExpression(this)

  def body: Option[ScExpression]
}