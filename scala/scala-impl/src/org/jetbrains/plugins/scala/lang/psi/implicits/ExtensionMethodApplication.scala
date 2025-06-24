package org.jetbrains.plugins.scala.lang.psi.implicits

import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.types.ScType

final case class ExtensionMethodApplication(
  resultType:           ScType,
  implicitArgsByClause: Seq[ImplicitArgumentsClause] = Seq.empty
)
