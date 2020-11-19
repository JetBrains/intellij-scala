package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScGivenPattern extends ScPattern {
  def typeElement: ScTypeElement
}
