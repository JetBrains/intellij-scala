package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

trait ScGivenAlias extends ScGiven with ScFunction {
  /**
   * @return None if given alias is incomplete (e.g. during editing the code)
   */
  def typeElement: Option[ScTypeElement]
}
