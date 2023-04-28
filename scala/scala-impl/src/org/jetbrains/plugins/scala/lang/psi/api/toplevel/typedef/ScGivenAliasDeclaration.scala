package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration

trait ScGivenAliasDeclaration extends ScGiven with ScFunctionDeclaration {
  def typeElement: ScTypeElement
}
