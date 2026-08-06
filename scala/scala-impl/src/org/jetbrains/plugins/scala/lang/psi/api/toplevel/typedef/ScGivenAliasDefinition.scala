package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

trait ScGivenAliasDefinition extends ScGivenAlias with ScFunctionDefinition {

  /**
   * @return true if the given is "deferred", like: {{{
   *           given T = scala.compiletime.deferred
   * }}}
   * @note actual since Scala 3.6
   * @see [[https://dotty.epfl.ch/docs/reference/contextual/deferred-givens.html]]
   */
  def isDeferred: Boolean
}
