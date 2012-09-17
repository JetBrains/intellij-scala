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

  def expr: Option[ScExpression]

  def isSimple: Boolean = pList.allPatternsSimple && bindings.size == 1

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPatternDefinition(this)
  }
}

object ScPatternDefinition {
  object expr {
    def unapply(definition: ScPatternDefinition): Option[ScExpression] = {
      if (definition == null) None
      else definition.expr
    }
  }
}