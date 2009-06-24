package org.jetbrains.plugins.scala.lang.psi.api.statements


import base.ScPatternList
import expr.ScExpression
import base.patterns.ScBindingPattern

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScVariableDefinition extends ScVariable {
  def pList: ScPatternList
  def bindings: Seq[ScBindingPattern]
  def declaredElements = bindings
  def expr = findChildByClass(classOf[ScExpression])
}