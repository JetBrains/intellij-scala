package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeArgs extends ScArguments {
  def typeArgsWithNamed: Seq[ScTypeArgument]

//  def typeArgs: Seq[ScTypeElement] =
//    typeArgsWithNamed.flatMap(_.typeElement)

  def namedTypeArgs: Seq[ScTypeArgument] =
    typeArgsWithNamed.filter(_.isNamed)

  def hasNamedTypeArgs: Boolean =
    namedTypeArgs.nonEmpty

  override def getArgsCount: Int = typeArgsWithNamed.length
}
