package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

trait ScGivenAlias extends ScGiven with ScFunction {
  def typeElement: ScTypeElement
}
