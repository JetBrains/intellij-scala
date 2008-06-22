package org.jetbrains.plugins.scala.lang.psi.api.statements

import base.patterns.ScBindingPattern
import expr.ScExpression

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScPatternDefinition extends ScValue {

  def bindings: Seq[ScBindingPattern]
  def expr = findChildByClass(classOf[ScExpression]) //not null, otherwise it is a different syntactic category
}