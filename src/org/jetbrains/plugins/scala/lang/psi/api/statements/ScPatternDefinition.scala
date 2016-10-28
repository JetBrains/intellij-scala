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

trait ScPatternDefinition extends ScValue {
  def pList: ScPatternList

  def bindings: Seq[ScBindingPattern]

  def expr: Option[ScExpression]

  def isSimple: Boolean = pList.simplePatterns && bindings.size == 1

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPatternDefinition(this)
  }
}

object ScPatternDefinition {
  object expr {
    def unapply(definition: ScPatternDefinition): Option[ScExpression] = Option(definition).flatMap(_.expr)
  }
}
