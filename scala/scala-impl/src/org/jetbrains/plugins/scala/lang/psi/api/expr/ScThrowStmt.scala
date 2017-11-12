package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
* @author Alexander Podkhalyuzin
*/
trait ScThrowStmt extends ScExpression {
  def body: Option[ScExpression] = findChild(classOf[ScExpression])

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitThrowExpression(this)
  }
}