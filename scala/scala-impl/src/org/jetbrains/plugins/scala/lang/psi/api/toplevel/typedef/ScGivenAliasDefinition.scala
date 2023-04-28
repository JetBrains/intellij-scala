package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

trait ScGivenAliasDefinition extends ScGiven with ScFunctionDefinition {
  def typeElement: ScTypeElement
}
