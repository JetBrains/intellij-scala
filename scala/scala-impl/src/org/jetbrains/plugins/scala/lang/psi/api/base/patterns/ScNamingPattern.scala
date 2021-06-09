package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/**
* @author Alexander Podkhalyuzin
*/

trait ScNamingPattern extends ScBindingPattern {
  def named: ScPattern = findChild[ScPattern].get
}

object ScNamingPattern {
  def unapply(pattern: ScNamingPattern): Some[ScPattern] =
    Some(pattern.named)
}