package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.types.ScType

case class ImplicitConversionApplication(
  resultType:           ScType,
  implicitArgsByClause: Seq[ImplicitArgumentsClause] = Seq.empty
)