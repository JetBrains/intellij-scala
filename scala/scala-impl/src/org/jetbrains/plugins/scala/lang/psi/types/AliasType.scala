package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

case class AliasType(ta: ScTypeAlias, lower: TypeResult, upper: TypeResult)
