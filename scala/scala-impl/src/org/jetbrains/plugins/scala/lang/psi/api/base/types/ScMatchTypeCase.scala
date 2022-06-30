package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScTypePattern

/**
 * A single case inside a match type `case P_i => T_i`, where
 * `P_i` is a type pattern and `T_i` is a type.
 */
trait ScMatchTypeCase extends ScalaPsiElement {
  def pattern: Option[ScTypePattern]

  def result: Option[ScTypeElement]
}

object ScMatchTypeCase {
  def unapply(cse: ScMatchTypeCase): Some[(Option[ScTypePattern], Option[ScTypeElement])] =
    Some((cse.pattern, cse.result))
}
