package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScTypeParamClauseStub extends StubElement[ScTypeParamClause] {
  def typeParameterClauseText: String
}