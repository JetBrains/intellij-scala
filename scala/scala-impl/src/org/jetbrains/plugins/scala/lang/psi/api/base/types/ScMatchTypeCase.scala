package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * A single case inside a match type `case P_i => T_i`, where
 * both `P_i` and `T_i` are type elements.
 */
trait ScMatchTypeCase extends ScalaPsiElement {
  def patternTypeElement: Option[ScTypeElement]
  def resultTypeElement: Option[ScTypeElement]

  def get: ScMatchTypeCase      = this
  def isEmpty: Boolean          = false
  def _1: Option[ScTypeElement] = patternTypeElement
  def _2: Option[ScTypeElement] = resultTypeElement
}

object ScMatchTypeCase {
  def unapply(cse: ScMatchTypeCase): ScMatchTypeCase = cse
}
