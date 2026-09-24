package org.jetbrains.plugins.scala.lang.psi.stubs


import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause

trait ScTypeParamClauseStub extends ScStubElement[ScTypeParamClause] {
  def typeParameterClauseText: String
}