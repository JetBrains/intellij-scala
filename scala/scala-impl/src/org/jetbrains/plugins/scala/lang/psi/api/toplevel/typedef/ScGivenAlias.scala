package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

trait ScGivenAlias extends ScGiven with ScFunctionDefinition {
  def typeElement: ScTypeElement
}
