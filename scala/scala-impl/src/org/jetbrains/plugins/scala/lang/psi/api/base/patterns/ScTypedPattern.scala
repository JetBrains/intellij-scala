package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * case x: Type =>
 *      ↑ This can only be a simple identifier or wildcard in Scala 2
 *        In Scala 3 t can also be a more complicated sub pattern See [[Sc3TypedPattern]]
 */
trait ScTypedPattern extends ScBindingPattern with ScTypedPatternLike

object ScTypedPattern {
  def unapply(pattern: ScTypedPattern): Option[ScTypeElement] =
    pattern.typePattern.map(_.typeElement)
}
