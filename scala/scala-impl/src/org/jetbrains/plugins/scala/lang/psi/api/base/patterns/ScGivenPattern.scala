package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId

trait ScGivenPattern extends ScBindingPattern {
  override def nameId: NameId.Immaterial
  def typeElement: ScTypeElement
}
