package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/**
 * @author Alexander Podkhalyuzin
 */

trait ScGuard extends ScalaPsiElement {
  def expr: Option[ScExpression]

  override def accept(visitor: ScalaElementVisitor) = visitor.visitGuard(this)
}