package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScGivenPattern extends ScBindingPattern {
  def typeElement: ScTypeElement
}
