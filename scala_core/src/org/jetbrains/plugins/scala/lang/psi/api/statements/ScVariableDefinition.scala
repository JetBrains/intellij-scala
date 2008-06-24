package org.jetbrains.plugins.scala.lang.psi.api.statements


import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScVariableDefinition extends ScVariable {
  def bindings: Seq[ScBindingPattern]
  def declaredElements = bindings
}