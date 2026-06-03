package org.jetbrains.plugins.scala.lang.psi.stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause

trait ScTypeParamClauseStub extends StubElement[ScTypeParamClause] {
  def typeParameterClauseText: String
}