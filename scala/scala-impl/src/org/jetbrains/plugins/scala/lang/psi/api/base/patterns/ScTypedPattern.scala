package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId

trait ScTypedPattern extends ScBindingPattern with ScTypedPatternLike {
  override def nameId: NameId.Placed
}

object ScTypedPattern {
  def unapply(pattern: ScTypedPattern): Option[ScTypeElement] =
    pattern.typePattern.map(_.typeElement)
}
