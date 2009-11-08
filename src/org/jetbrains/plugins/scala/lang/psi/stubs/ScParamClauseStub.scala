package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import api.statements.params.ScParameterClause
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

trait ScParamClauseStub  extends StubElement[ScParameterClause]{
  def isImplicit: Boolean
}