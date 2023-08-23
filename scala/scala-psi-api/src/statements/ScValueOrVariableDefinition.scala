package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

trait ScValueOrVariableDefinition extends ScValueOrVariable with ScDefinitionWithAssignment {

  def pList: ScPatternList

  def bindings: Seq[ScBindingPattern]

  def expr: Option[ScExpression]

  def isSimple: Boolean

  override def isAbstract: Boolean
}
