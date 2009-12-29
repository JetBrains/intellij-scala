package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/** 
* @author Alexander Podkhalyuzin
*/

trait ScTryStmt extends ScExpression {
  def tryBlock = findChildByClassScala(classOf[ScTryBlock])
  def catchBlock = findChild(classOf[ScCatchBlock])
  def finallyBlock = findChild(classOf[ScFinallyBlock])

  override def accept(visitor: ScalaElementVisitor) = visitor.visitTryExpression(this)
}