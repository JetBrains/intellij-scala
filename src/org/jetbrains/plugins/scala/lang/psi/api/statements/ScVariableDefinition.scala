package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScVariableDefinition extends ScVariable {
  def pList: ScPatternList

  def bindings: Seq[ScBindingPattern]

  def declaredElements = bindings

  def expr: Option[ScExpression]

  def hasExplicitType: Boolean = typeElement.isDefined

  def isSimple: Boolean = pList.allPatternsSimple && bindings.size == 1

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitVariableDefinition(this)
  }
}

object ScVariableDefinition {
  object expr {
    def unapply(definition: ScVariableDefinition) = definition.expr
  }
}