package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait Sc3TypedPatternBase extends ScPatternBase { this: Sc3TypedPattern =>
  def pattern: Option[ScPattern]
  def typePattern: Option[ScTypePattern]
}

abstract class Sc3TypedPatternCompanion {
  def unapply(pattern: Sc3TypedPattern): Option[(ScPattern, ScTypeElement)] =
    pattern.pattern zip pattern.typePattern.map(_.typeElement)
}