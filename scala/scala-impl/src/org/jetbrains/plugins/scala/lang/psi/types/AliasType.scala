package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

case class AliasType(ta: ScTypeAlias, lower: TypeResult, upper: TypeResult)

object AliasType {
  def unapply(tpe: ScType): Option[(ScTypeAlias, TypeResult, TypeResult)] =
    tpe.aliasType.map(atpe => (atpe.ta, atpe.lower, atpe.upper))
}
