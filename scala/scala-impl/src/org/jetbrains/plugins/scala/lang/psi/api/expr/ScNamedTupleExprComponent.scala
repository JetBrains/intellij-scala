package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.base.ScNamedTupleComponent

trait ScNamedTupleExprComponent extends ScNamedTupleComponent {
  final def namedTuple: ScNamedTuple = getParent.asInstanceOf[ScNamedTuple]
  def expr: Option[ScExpression]
}

