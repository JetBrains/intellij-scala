package org.jetbrains.plugins.scala.lang.psi.types.api.presentation

import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait TypeRenderer {
  def render(typ: ScType): String
  final def apply(typ: ScType): String = render(typ)
}