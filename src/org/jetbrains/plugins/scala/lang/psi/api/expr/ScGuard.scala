package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
 * @author Alexander Podkhalyuzin
 */

trait ScGuard extends ScalaPsiElement {
  def expr: Option[ScExpression]

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}