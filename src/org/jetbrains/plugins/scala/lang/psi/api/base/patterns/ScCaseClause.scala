package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import psi.ScalaPsiElement
import expr.{ScExpression, ScGuard}
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClause extends ScalaPsiElement {
  def pattern = findChild(classOf[ScPattern])
  def expr = findChild(classOf[ScExpression])
  def guard = findChild(classOf[ScGuard])
  override def accept(visitor: ScalaElementVisitor) = visitor.visitCaseClause(this)
}