package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import base.ScPatternList
import expr.ScExpression
import base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScVariableDefinition extends ScVariable {
  def pList: ScPatternList
  def bindings: Seq[ScBindingPattern]
  def declaredElements = bindings
  def expr = findChildByClassScala(classOf[ScExpression])
  override def accept(visitor: ScalaElementVisitor) = visitor.visitVariableDefinition(this)
}