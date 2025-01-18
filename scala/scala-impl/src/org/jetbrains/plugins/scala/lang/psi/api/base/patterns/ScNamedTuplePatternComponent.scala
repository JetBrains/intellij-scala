package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.ScNamedTupleComponent

trait ScNamedTuplePatternComponent extends ScNamedTupleComponent {
  final def namedTuplePattern: ScNamedTuplePattern = getParent.asInstanceOf[ScNamedTuplePattern]
  def subPattern: Option[ScPattern] = findChild[ScPattern]
}

object ScNamedTuplePatternComponent {
  def unapply(comp: ScNamedTuplePatternComponent): Option[(String, ScPattern)] = {
    comp.nameElement.map(_.getText).zip(comp.subPattern)
  }
}