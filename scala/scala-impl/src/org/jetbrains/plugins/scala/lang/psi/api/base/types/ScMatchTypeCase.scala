package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * A single case inside a match type `case P_i => T_i`, where
 * both `P_i` and `T_i` are type elements.
 */
trait ScMatchTypeCase extends ScalaPsiElement {
  def pattern: Option[ScTypeElement]

  def result: Option[ScTypeElement]
}

object ScMatchTypeCase {
  def unapply(cse: ScMatchTypeCase): Some[(Option[ScTypeElement], Option[ScTypeElement])] =
    Some((cse.pattern, cse.result))
}
