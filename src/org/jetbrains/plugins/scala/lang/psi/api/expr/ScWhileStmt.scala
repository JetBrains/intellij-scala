package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr


/** 
* @author Alexander Podkhalyuzin
*/

trait ScWhileStmt extends ScExpression {
  def condition: Option[ScExpression]

  def body: Option[ScExpression]

  override def accept(visitor: ScalaElementVisitor) = visitor.visitWhileStatement(this)
}