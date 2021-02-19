package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api._


import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScGivenPatternBase extends ScPatternBase { this: ScGivenPattern =>
  def typeElement: ScTypeElement
}