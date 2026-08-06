package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeArgs extends ScArguments {
  def typeArguments: Seq[ScTypeArgument]

  def namedTypeArgs: Seq[ScTypeArgument] =
    typeArguments.filter(_.isNamed)

  def hasNamedTypeArgs: Boolean =
    namedTypeArgs.nonEmpty

  override def getArgsCount: Int = typeArguments.length
}
