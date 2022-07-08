package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

trait ScPatternDefinition extends ScValue with ScValueOrVariableDefinition {

  override def isSimple: Boolean = pList.simplePatterns && bindings.size == 1

  override def isAbstract: Boolean = false

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPatternDefinition(this)
  }
}

object ScPatternDefinition {
  object expr {
    def unapply(definition: ScPatternDefinition): Option[ScExpression] = definition.expr
  }
}
