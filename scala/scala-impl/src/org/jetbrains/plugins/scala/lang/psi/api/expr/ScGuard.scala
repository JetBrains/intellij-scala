package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
  * @author Alexander Podkhalyuzin
  */

trait ScGuard extends ScEnumerator {
  def expr: Option[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}