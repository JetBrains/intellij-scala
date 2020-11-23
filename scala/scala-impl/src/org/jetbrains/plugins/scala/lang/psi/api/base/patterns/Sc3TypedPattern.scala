package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait Sc3TypedPattern extends ScPattern {
  def pattern: Option[ScPattern]
  def typePattern: Option[ScTypePattern]
}

object Sc3TypedPattern {
  def unapply(pattern: Sc3TypedPattern): Option[(ScPattern, ScTypeElement)] =
    pattern.pattern zip pattern.typePattern.map(_.typeElement)
}
