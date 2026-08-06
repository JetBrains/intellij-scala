package org.jetbrains.plugins.scala.lang.psi.api.base
package patterns

trait ScNamingPattern extends ScBindingPattern {
  def named: ScPattern = findChild[ScPattern].get
}

object ScNamingPattern {
  def unapply(pattern: ScNamingPattern): Some[ScPattern] =
    Some(pattern.named)
}