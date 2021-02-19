package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * A single case inside a match type `case P_i => T_i`, where
 * both `P_i` and `T_i` are type elements.
 */
trait ScMatchTypeCaseBase extends ScalaPsiElementBase { this: ScMatchTypeCase =>
  def patternTypeElement: Option[ScTypeElement]
  def resultTypeElement: Option[ScTypeElement]
}

abstract class ScMatchTypeCaseCompanion {
  def unapply(cse: ScMatchTypeCase): Some[(Option[ScTypeElement], Option[ScTypeElement])] =
    Some((cse.patternTypeElement, cse.resultTypeElement))
}