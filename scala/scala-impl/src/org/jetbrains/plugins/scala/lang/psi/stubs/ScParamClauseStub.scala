package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

trait ScParamClauseStub extends ScStubElement[ScParameterClause] {
  def hasImplicitKeyword: Boolean
  def hasUsingKeyword: Boolean
}