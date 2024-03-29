package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeArgs extends ScArguments {
  def typeArgs: Seq[ScTypeElement]

  override def getArgsCount: Int = typeArgs.length
}