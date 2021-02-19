package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api._


/**
 * Type element representing Scala 3 match type definitions
 * of shape `X match { P_1 => T_1, ... P_n => T_n }`,
 * where each case `P_i => T_i` is either an unary function type
 * (if there are no bound type parameters) or a type lambda over an
 * unary function.
 */
trait ScMatchTypeElementBase extends ScTypeElementBase { this: ScMatchTypeElement =>
  override protected val typeName: String = "MatchType"

  def scrutineeTypeElement: ScTypeElement
  def cases: Option[ScMatchTypeCases]
}

abstract class ScMatchTypeElementCompanion {
  def unapply(mte: ScMatchTypeElement): Some[(ScTypeElement, Option[ScMatchTypeCases])] =
    Some((mte.scrutineeTypeElement, mte.cases))
}