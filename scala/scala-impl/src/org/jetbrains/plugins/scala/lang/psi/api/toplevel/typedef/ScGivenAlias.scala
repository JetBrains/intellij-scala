package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

trait ScGivenAlias extends ScGivenInstance with ScValueOrVariable {
  def expr: Option[ScExpression]
}
