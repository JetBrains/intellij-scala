package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
* @author Alexander Podkhalyuzin
*/
trait ScThrow extends ScExpression {
  def expression: Option[ScExpression] = findChild(classOf[ScExpression])

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitThrow(this)
  }
}