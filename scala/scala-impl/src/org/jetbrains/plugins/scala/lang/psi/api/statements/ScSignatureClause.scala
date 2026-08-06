package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParamClause}

sealed trait ScSignatureClause

object ScSignatureClause {
  final case class TypeClause(clause: ScTypeParamClause) extends ScSignatureClause

  final case class TermClause(clause: ScParameterClause) extends ScSignatureClause
}

