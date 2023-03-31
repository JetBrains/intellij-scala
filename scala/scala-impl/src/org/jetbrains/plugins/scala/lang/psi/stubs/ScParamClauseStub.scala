package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

trait ScParamClauseStub extends StubElement[ScParameterClause] {
  def isImplicit: Boolean
  def isUsing: Boolean
}