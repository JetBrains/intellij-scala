package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

case class AliasType(ta: ScTypeAlias, lower: TypeResult, upper: TypeResult, effectivelyOpaque: Boolean)

object AliasType {
  def unapply(tpe: ScType)(implicit context: Context): Option[(ScTypeAlias, TypeResult, TypeResult, Boolean)] =
    tpe.aliasType.map(atpe => (atpe.ta, atpe.lower, atpe.upper, atpe.effectivelyOpaque))
}
