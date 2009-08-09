package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import base.patterns.ScBindingPattern
import base.ScPatternList
import expr.ScExpression

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScPatternDefinition extends ScValue {
  def pList: ScPatternList
  def bindings: Seq[ScBindingPattern]
  def expr: ScExpression = findChildByClass(classOf[ScExpression]) //not null, otherwise it is a different syntactic category
}