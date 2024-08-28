package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * Compared to [[ScTypedPattern]] the Scala 3 version can have further potentially complicated subpatterns.
 *
 *  case (SubPattern): Type =>
 */
trait Sc3TypedPattern extends ScTypedPatternLike {
  def pattern: ScPattern
}

object Sc3TypedPattern {
  def unapply(pattern: Sc3TypedPattern): Option[(ScPattern, ScTypeElement)] =
    pattern.typePattern.map(tp => (pattern.pattern, tp.typeElement))
}
