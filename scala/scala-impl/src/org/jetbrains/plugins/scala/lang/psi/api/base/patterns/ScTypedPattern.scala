package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Alexander Podkhalyuzin
 */
trait ScTypedPattern extends ScBindingPattern with ScTypedPatternLike

object ScTypedPattern {
  def unapply(pattern: ScTypedPattern): Option[ScTypeElement] =
    pattern.typePattern.map(_.typeElement)
}
