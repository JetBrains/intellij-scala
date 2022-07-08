package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

trait ScNamingPattern extends ScBindingPattern {
  def named: ScPattern = findChild[ScPattern].get
}

object ScNamingPattern {
  def unapply(pattern: ScNamingPattern): Some[ScPattern] =
    Some(pattern.named)
}